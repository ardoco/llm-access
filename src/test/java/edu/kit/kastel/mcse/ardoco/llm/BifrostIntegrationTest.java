/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.embedding.EmbeddingCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.chat.CachingChatModel;
import edu.kit.kastel.mcse.ardoco.llm.chat.ChatModelPlatform;
import edu.kit.kastel.mcse.ardoco.llm.chat.ChatModelProvider;
import edu.kit.kastel.mcse.ardoco.llm.chat.LlmConfiguration;
import edu.kit.kastel.mcse.ardoco.llm.embedding.EmbeddingConfiguration;
import edu.kit.kastel.mcse.ardoco.llm.embedding.EmbeddingCreator;
import edu.kit.kastel.mcse.ardoco.llm.embedding.EmbeddingPlatform;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;
import edu.kit.kastel.mcse.ardoco.llm.util.SystemEnvironment;

/**
 * Live test against a real Bifrost gateway: one tiny chat request, one embedding request, and checks that Bifrost
 * models share their cache identity with the native OpenAI platform. The cache-hit tests seed the native identity
 * and must be answered from the cache without any request. The class is skipped unless both
 * {@code BIFROST_API_KEY} and {@code BIFROST_URL} are set.
 */
@NullMarked
@EnabledIfEnvironmentVariable(named = "BIFROST_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BIFROST_URL", matches = ".+")
class BifrostIntegrationTest {

    private static final String SENTINEL = "SENTINEL-FROM-NATIVE-OPENAI-CACHE";

    private final EnvironmentProvider environment = new SystemEnvironment();
    private final EnvironmentProvider cacheEnvironment = new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR"));

    @TempDir
    private Path tempCacheDir;

    @Test
    @DisplayName("Bifrost answers a chat request")
    void chat() {
        LlmConfiguration configuration = LlmConfiguration.builder(ChatModelPlatform.BIFROST).modelName("openai/gpt-4o-mini").environment(environment).build();

        String answer = new ChatModelProvider(configuration).createChatModel().chat("Reply with OK");

        assertNotNull(answer);
        assertFalse(answer.isBlank());
    }

    @Test
    @DisplayName("Bifrost returns an embedding")
    void embedding() throws IOException {
        EmbeddingConfiguration configuration = EmbeddingConfiguration.builder(EmbeddingPlatform.BIFROST)
                .modelName("openai/text-embedding-3-small")
                .environment(environment)
                .build();
        CacheManager cacheManager = new CacheManager(tempCacheDir, cacheEnvironment);

        float[] embedding = EmbeddingCreator.create(configuration, cacheManager).calculateEmbedding("hello world");
        cacheManager.flush();

        assertTrue(embedding.length > 0);
        assertTrue(Files.exists(tempCacheDir.resolve("Embedding_text-embedding-3-small.json")));
    }

    @Test
    @DisplayName("Bifrost chat hits an entry cached under the native OpenAI identity")
    void chatHitsNativeCacheEntry() throws IOException {
        String prompt = "Reply with OK";
        // The native configuration is only used for its cache identity; no OpenAI key is needed and OpenAI is never called.
        LlmConfiguration nativeConfiguration = LlmConfiguration.builder(ChatModelPlatform.OPENAI).modelName("gpt-4o-mini").environment(environment).build();
        LlmConfiguration bifrostConfiguration = LlmConfiguration.builder(ChatModelPlatform.BIFROST)
                .modelName("openai/gpt-4o-mini")
                .seed(nativeConfiguration.seed())
                .temperature(nativeConfiguration.temperature())
                .environment(environment)
                .build();

        CacheManager seedingManager = new CacheManager(tempCacheDir, cacheEnvironment);
        ChatModel sentinelModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder().aiMessage(AiMessage.from(SENTINEL)).build();
            }
        };
        new CachingChatModel(sentinelModel, seedingManager.getCache(new ChatModelProvider(nativeConfiguration).cacheParameters())).chat(prompt);
        seedingManager.flush();

        CacheManager bifrostManager = new CacheManager(tempCacheDir, cacheEnvironment);
        ChatModelProvider bifrostProvider = new ChatModelProvider(bifrostConfiguration);
        ChatModel bifrostModel = new CachingChatModel(bifrostProvider.createChatModel(), bifrostManager.getCache(bifrostProvider.cacheParameters()));

        assertEquals(SENTINEL, bifrostModel.chat(prompt));
    }

    @Test
    @DisplayName("Bifrost embedding hits an entry cached under the native OpenAI identity")
    void embeddingHitsNativeCacheEntry() throws IOException {
        String text = "hello world";
        float[] sentinel = { 1.5f, -2.5f, 3.25f };
        CacheManager seedingManager = new CacheManager(tempCacheDir, cacheEnvironment);
        // Same value format as CachedEmbeddingCreator: float[] stored under the text.
        seedingManager.getCache(new EmbeddingCacheParameter("text-embedding-3-small")).put(text, sentinel);
        seedingManager.flush();

        EmbeddingConfiguration configuration = EmbeddingConfiguration.builder(EmbeddingPlatform.BIFROST)
                .modelName("openai/text-embedding-3-small")
                .environment(environment)
                .build();
        CacheManager bifrostManager = new CacheManager(tempCacheDir, cacheEnvironment);

        assertArrayEquals(sentinel, EmbeddingCreator.create(configuration, bifrostManager).calculateEmbedding(text));
    }
}
