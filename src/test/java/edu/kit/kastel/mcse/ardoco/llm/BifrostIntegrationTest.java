/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
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
 * Live smoke test against a real Bifrost gateway: one tiny chat request and one embedding request. It is
 * skipped unless {@code BIFROST_API_KEY} is set; {@code BIFROST_URL} optionally overrides the default gateway.
 */
@NullMarked
@EnabledIfEnvironmentVariable(named = "BIFROST_API_KEY", matches = ".+")
class BifrostIntegrationTest {

    private final EnvironmentProvider environment = new SystemEnvironment();

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
        CacheManager cacheManager = new CacheManager(tempCacheDir, new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY",
                "ERROR")));

        float[] embedding = EmbeddingCreator.create(configuration, cacheManager).calculateEmbedding("hello world");

        assertTrue(embedding.length > 0);
    }
}
