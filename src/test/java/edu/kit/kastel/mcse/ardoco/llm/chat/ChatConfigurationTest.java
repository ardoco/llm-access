/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;

/**
 * Tests for the framework-neutral chat configuration API ({@link LlmConfiguration},
 * {@link ChatModelPlatform}, and {@link ChatModelProvider} metadata).
 */
@NullMarked
class ChatConfigurationTest {

    @Test
    @DisplayName("of() sets platform and model with default seed and temperature")
    void testOf() {
        LlmConfiguration config = LlmConfiguration.of(ChatModelPlatform.OPENAI, "gpt-4o-mini");
        assertEquals(ChatModelPlatform.OPENAI, config.platform());
        assertEquals("gpt-4o-mini", config.modelName());
        assertEquals(LlmConfiguration.DEFAULT_SEED, config.seed());
        assertEquals(LlmConfiguration.DEFAULT_TEMPERATURE, config.temperature());
    }

    @Test
    @DisplayName("builder overrides individual settings")
    void testBuilderOverrides() {
        LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OLLAMA).modelName("mistral").seed(7).temperature(0.5).build();
        assertEquals(ChatModelPlatform.OLLAMA, config.platform());
        assertEquals("mistral", config.modelName());
        assertEquals(7, config.seed());
        assertEquals(0.5, config.temperature());
    }

    @Test
    @DisplayName("build requires a model name")
    void testBuilderRequiresModel() {
        assertThrows(IllegalArgumentException.class, () -> LlmConfiguration.builder(ChatModelPlatform.DEEPSEEK).build());
        assertThrows(IllegalArgumentException.class, () -> LlmConfiguration.builder(ChatModelPlatform.DEEPSEEK).modelName("  ").build());
    }

    @Test
    @DisplayName("fromString resolves platforms case-insensitively")
    void testFromString() {
        assertEquals(ChatModelPlatform.OPENAI, ChatModelPlatform.fromString("openai"));
        assertEquals(ChatModelPlatform.BLABLADOR, ChatModelPlatform.fromString("BlaBlaDor"));
        assertThrows(IllegalArgumentException.class, () -> ChatModelPlatform.fromString("nope"));
    }

    @Test
    @DisplayName("provider exposes cache parameters")
    void testProviderMetadata() {
        LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OPENAI).modelName("gpt-4o").seed(133742243).temperature(0.0).build();
        ChatModelProvider provider = new ChatModelProvider(config);

        assertEquals("gpt-4o", provider.modelName());

        ChatCacheParameter parameters = provider.cacheParameters();
        assertEquals(new ChatCacheParameter("gpt-4o", 133742243, 0.0), parameters);
        // Backward-compatible file identifier omits temperature when 0.0
        assertEquals("gpt-4o_133742243", parameters.parameters());
    }
}
