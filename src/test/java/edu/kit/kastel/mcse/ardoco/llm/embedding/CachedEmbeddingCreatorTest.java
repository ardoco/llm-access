/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;

/**
 * Tests the caching, ordering, and parameter-forwarding behaviour of {@link CachedEmbeddingCreator} through
 * a recording subclass whose model is a stub that counts calls and produces a deterministic vector.
 */
@NullMarked
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachedEmbeddingCreatorTest {
    private final EnvironmentProvider environment = new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR"));
    private static final EnvironmentProvider EMPTY_ENVIRONMENT = new MapEnvironment(Map.of());

    @TempDir
    private Path tempCacheDir;

    private CacheManager cacheManager;

    @BeforeEach
    void setup() throws IOException {
        cacheManager = new CacheManager(tempCacheDir, environment);
        RecordingEmbeddingCreator.embedCalls.set(0);
        RecordingEmbeddingCreator.paramsSeen.clear();
        RecordingEmbeddingCreator.environmentsSeen.clear();
    }

    @Test
    @DisplayName("an embedding is computed once and then served from the cache")
    void cachesEmbeddings() {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "cache-model", 1);

        float[] first = creator.calculateEmbedding("hello");
        float[] second = creator.calculateEmbedding("hello");

        assertArrayEquals(first, second);
        assertEquals(1, RecordingEmbeddingCreator.embedCalls.get(), "the second call must hit the cache");
    }

    @Test
    @DisplayName("embeddings are returned in input order")
    void preservesOrder() {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "order-model", 1);
        List<float[]> embeddings = creator.calculateEmbeddings(List.of("a", "bb", "ccc"));

        assertEquals(3, embeddings.size());
        assertArrayEquals(new float[] { 'a' }, embeddings.get(0));
        assertArrayEquals(new float[] { 'b' }, embeddings.get(1));
        assertArrayEquals(new float[] { 'c' }, embeddings.get(2));
    }

    @Test
    @DisplayName("an empty input yields an empty result without touching the model")
    void emptyInput() {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "empty-model", 1);
        assertTrue(creator.calculateEmbeddings(List.of()).isEmpty());
        assertEquals(0, RecordingEmbeddingCreator.embedCalls.get());
    }

    @Test
    @DisplayName("cached embeddings survive a reload from disk")
    void persistsAcrossReload() throws IOException {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "persist-model", 1);
        creator.calculateEmbedding("hello");
        cacheManager.flush();

        RecordingEmbeddingCreator.embedCalls.set(0);
        cacheManager = new CacheManager(tempCacheDir, environment);
        RecordingEmbeddingCreator reloaded = new RecordingEmbeddingCreator(cacheManager, "persist-model", 1);
        float[] cached = reloaded.calculateEmbedding("hello");

        assertArrayEquals(new float[] { 'h' }, cached);
        assertEquals(0, RecordingEmbeddingCreator.embedCalls.get(), "the value must come from disk, not the model");
    }

    @Test
    @DisplayName("the parallel path forwards the model parameters to createEmbeddingModel")
    void parallelPathForwardsParameters() {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "params-model", 2, "p1", "p2");
        creator.calculateEmbeddings(List.of("a", "b"));

        // The parallel branch (threads > 1, more than one element) must build its per-thread models with the params.
        assertTrue(RecordingEmbeddingCreator.paramsSeen.size() >= 2, "parallel path should build per-thread models");
        for (String[] seen : RecordingEmbeddingCreator.paramsSeen) {
            assertArrayEquals(new String[] { "p1", "p2" }, seen);
        }
    }

    @Test
    @DisplayName("the injected environment is available while the superclass constructor builds the model")
    void injectedEnvironmentIsUsedDuringConstruction() {
        EnvironmentProvider injected = new MapEnvironment(Map.of("SOME_KEY", "some-value"));
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "env-model", 1, injected);

        assertSame(injected, creator.environment());
        assertEquals(List.of(injected), RecordingEmbeddingCreator.environmentsSeen, "createEmbeddingModel must see the injected environment");
    }

    @Test
    @DisplayName("EmbeddingCreator.create reads credentials from the configured environment")
    void createUsesConfiguredEnvironment() {
        EmbeddingConfiguration withoutKey = EmbeddingConfiguration.builder(EmbeddingPlatform.OPENAI)
                .modelName("text-embedding-3-small")
                .environment(EMPTY_ENVIRONMENT)
                .build();
        assertThrows(IllegalStateException.class, () -> EmbeddingCreator.create(withoutKey, cacheManager));

        EmbeddingConfiguration withKey = EmbeddingConfiguration.builder(EmbeddingPlatform.OPENAI)
                .modelName("text-embedding-3-small")
                .environment(new MapEnvironment(Map.of("OPENAI_API_KEY", "dummy")))
                .build();
        assertInstanceOf(OpenAiEmbeddingCreator.class, EmbeddingCreator.create(withKey, cacheManager));
    }

    @Test
    @DisplayName("EmbeddingCreator.create passes the configured environment to the Open WebUI creator")
    void createPassesEnvironmentToOpenWebUi() {
        EmbeddingConfiguration configuration = EmbeddingConfiguration.builder(EmbeddingPlatform.OPENWEBUI)
                .modelName("nomic-embed-text:v1.5")
                .environment(EMPTY_ENVIRONMENT)
                .build();
        assertThrows(IllegalStateException.class, () -> EmbeddingCreator.create(configuration, cacheManager));
    }

    @Test
    @DisplayName("the embedding cache is created by the injected cache manager")
    void usesInjectedCacheManager() throws IOException {
        RecordingEmbeddingCreator creator = new RecordingEmbeddingCreator(cacheManager, "manager-model", 1);
        creator.calculateEmbedding("hello");
        cacheManager.flush();

        assertTrue(Files.exists(tempCacheDir.resolve("RecordingEmbeddingCreator_manager-model.json")), "the cache file must be in the manager's directory");
    }

    @Test
    @DisplayName("EmbeddingCreator.create uses the cache settings of the injected cache manager")
    void createUsesInjectedCacheManager() throws IOException {
        // Port 1 is never a Redis server, so creating the REDIS cache must fail.
        EnvironmentProvider redisEnvironment = new MapEnvironment(Map.of("CACHE_HIERARCHY", "REDIS", "REDIS_URL", "redis://127.0.0.1:1"));
        CacheManager redisCacheManager = new CacheManager(tempCacheDir, redisEnvironment);
        EmbeddingConfiguration configuration = EmbeddingConfiguration.builder(EmbeddingPlatform.OPENAI)
                .modelName("text-embedding-3-small")
                .environment(new MapEnvironment(Map.of("OPENAI_API_KEY", "dummy")))
                .build();

        assertThrows(RuntimeException.class, () -> EmbeddingCreator.create(configuration, redisCacheManager));
        assertInstanceOf(OpenAiEmbeddingCreator.class, EmbeddingCreator.create(configuration, cacheManager));
    }

    /**
     * A concrete {@link CachedEmbeddingCreator} whose model is a stub: it counts embedding calls, records the
     * parameters passed to {@link #createEmbeddingModel}, and returns a deterministic vector derived from the
     * first character of the input.
     */
    private static final class RecordingEmbeddingCreator extends CachedEmbeddingCreator {
        static final AtomicInteger embedCalls = new AtomicInteger();
        static final List<String[]> paramsSeen = new CopyOnWriteArrayList<>();
        static final List<EnvironmentProvider> environmentsSeen = new CopyOnWriteArrayList<>();

        RecordingEmbeddingCreator(CacheManager cacheManager, String model, int threads, String... params) {
            this(cacheManager, model, threads, EMPTY_ENVIRONMENT, params);
        }

        RecordingEmbeddingCreator(CacheManager cacheManager, String model, int threads, EnvironmentProvider environment, String... params) {
            super(model, threads, environment, cacheManager, params);
        }

        @Override
        protected EmbeddingModel createEmbeddingModel(String model, String... params) {
            paramsSeen.add(params);
            environmentsSeen.add(environment());
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
                embedCalls.incrementAndGet();
                String text = invocation.getArgument(0);
                return Response.from(Embedding.from(new float[] { text.charAt(0) }));
            });
            return embeddingModel;
        }
    }
}
