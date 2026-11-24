/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.ClearLocalCachesExtension;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;

/**
 * Tests how embedding creators deal with transient and input-length errors, using a local HTTP stub that speaks the
 * OpenAI embeddings protocol (through the Bifrost creator, whose URL is configurable) and a failing stub model.
 */
@NullMarked
@ExtendWith(ClearLocalCachesExtension.class)
class EmbeddingRetryTest {
    private static final String MODEL = "openai/text-embedding-3-small";

    @TempDir
    private Path tempCacheDir;

    private HttpServer server;
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile Stub stub = (count, body) -> new Reply(200, EMBEDDING_RESPONSE);

    private CacheManager cacheManager;
    private EnvironmentProvider environment;

    private static final String EMBEDDING_RESPONSE = """
            {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.5,0.25]}],"model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}""";

    private record Reply(int status, String body) {
    }

    @FunctionalInterface
    private interface Stub {
        Reply answer(int requestNumber, String body);
    }

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            Reply reply = stub.answer(requests.incrementAndGet(), body);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        environment = new MapEnvironment(Map.of("BIFROST_API_KEY", "dummy", "BIFROST_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"));
        cacheManager = new CacheManager(tempCacheDir, new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("a transient 429 followed by success yields the embedding without the length fallback")
    void transientRateLimitIsRetried() {
        stub = (count, body) -> count == 1 ?
                new Reply(429, "{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit\"}}") :
                new Reply(200, EMBEDDING_RESPONSE);

        float[] embedding = new BifrostEmbeddingCreator(MODEL, environment, cacheManager).calculateEmbedding("hello");

        assertArrayEquals(new float[] { 0.5f, 0.25f }, embedding);
        assertEquals(2, requests.get(), "the failed request is repeated once, unchanged");
        assertEquals(requestBodies.get(0), requestBodies.get(1));
    }

    @Test
    @DisplayName("a transient 503 followed by success yields the embedding")
    void transientServerErrorIsRetried() {
        stub = (count, body) -> count == 1 ? new Reply(503, "{\"error\":{\"message\":\"unavailable\"}}") : new Reply(200, EMBEDDING_RESPONSE);

        float[] embedding = new BifrostEmbeddingCreator(MODEL, environment, cacheManager).calculateEmbedding("hello");

        assertArrayEquals(new float[] { 0.5f, 0.25f }, embedding);
        assertEquals(2, requests.get());
    }

    @Test
    @DisplayName("a context-length error is not retried and still triggers the token-length fallback")
    void lengthErrorTriggersFallback() {
        List<Integer> inputLengths = new ArrayList<>();
        stub = (count, body) -> {
            inputLengths.add(body.length());
            return count == 1 ?
                    new Reply(400, "{\"error\":{\"message\":\"maximum context length is 8192 tokens\",\"code\":\"context_length_exceeded\"}}") :
                    new Reply(200, EMBEDDING_RESPONSE);
        };
        String content = "word ".repeat(30_000);

        float[] embedding = new BifrostEmbeddingCreator(MODEL, environment, cacheManager).calculateEmbedding(content);

        assertArrayEquals(new float[] { 0.5f, 0.25f }, embedding);
        assertEquals(2, requests.get(), "one rejected request, one request with the truncated input");
        assertTrue(inputLengths.get(1) < inputLengths.get(0));
    }

    @Test
    @DisplayName("a persistent transient error is rethrown as such instead of being reported as a token-length problem")
    void persistentTransientErrorIsRethrown() {
        stub = (count, body) -> new Reply(429, "{\"error\":{\"message\":\"rate limited\"}}");
        BifrostEmbeddingCreator creator = new BifrostEmbeddingCreator(MODEL, environment, cacheManager);

        // The parallel path wraps the failure; the root cause must still be the rate limit, not the length fallback.
        Throwable thrown = assertThrows(RuntimeException.class, () -> creator.calculateEmbedding("hello"));
        while (thrown.getCause() != null && !(thrown instanceof RateLimitException)) {
            thrown = thrown.getCause();
        }
        assertInstanceOf(RateLimitException.class, thrown);
    }

    @Test
    @DisplayName("a non-length error on a short input surfaces the original error as cause")
    void otherErrorKeepsCause() {
        IllegalStateException failure = new IllegalStateException("boom");
        CachedEmbeddingCreator creator = new CachedEmbeddingCreator("text-embedding-3-small", 1, new MapEnvironment(Map.of()), cacheManager) {
            @Override
            protected EmbeddingModel createEmbeddingModel(String model, String... params) {
                return new EmbeddingModel() {
                    @Override
                    public dev.langchain4j.model.output.Response<List<dev.langchain4j.data.embedding.Embedding>> embedAll(
                            List<dev.langchain4j.data.segment.TextSegment> textSegments) {
                        throw failure;
                    }
                };
            }
        };

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> creator.calculateEmbedding("short"));
        assertSame(failure, thrown.getCause());
    }
}
