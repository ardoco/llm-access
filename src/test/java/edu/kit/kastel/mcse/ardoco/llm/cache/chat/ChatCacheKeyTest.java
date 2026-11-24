/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import edu.kit.kastel.mcse.ardoco.llm.chat.ChatModelPlatform;
import edu.kit.kastel.mcse.ardoco.llm.chat.ChatModelProvider;
import edu.kit.kastel.mcse.ardoco.llm.chat.LlmConfiguration;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.KeyGenerator;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;

/**
 * Tests for {@link ChatCacheParameter} and {@link ChatCacheKey}, including the backward-compatible file
 * identifier and JSON key format.
 */
@NullMarked
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatCacheKeyTest {

    @Test
    @DisplayName("parameters() omits the temperature when it is 0.0 (backward compatibility)")
    void parametersOmitZeroTemperature() {
        assertEquals("gpt-4o_42", new ChatCacheParameter("gpt-4o", 42, 0.0).parameters());
    }

    @Test
    @DisplayName("parameters() includes a non-zero temperature")
    void parametersIncludeTemperature() {
        assertEquals("gpt-4o_42_0.5", new ChatCacheParameter("gpt-4o", 42, 0.5).parameters());
    }

    @Test
    @DisplayName("createCacheKey derives the local key from the content")
    void createCacheKey() {
        ChatCacheKey key = new ChatCacheParameter("gpt-4o", 42, 0.0).createCacheKey("hello");
        assertEquals("gpt-4o", key.model());
        assertEquals("hello", key.content());
        assertEquals(KeyGenerator.generateKey("hello"), key.localKey());
    }

    @Test
    @DisplayName("toJsonKey serializes the identifying fields but not the local key")
    void jsonKeyExcludesLocalKey() {
        ChatCacheKey key = new ChatCacheParameter("gpt-4o", 42, 0.0).createCacheKey("hello");
        String json = key.toJsonKey();
        assertTrue(json.contains("gpt-4o"), json);
        assertTrue(json.contains("CHAT"), json);
        assertTrue(json.contains("hello"), json);
        assertFalse(json.contains(key.localKey()), "local key must be excluded from the JSON key");
    }

    @Test
    @DisplayName("keys are equal for the same parameter and content, distinct otherwise")
    void equality() {
        ChatCacheParameter parameter = new ChatCacheParameter("gpt-4o", 42, 0.0);
        assertEquals(parameter.createCacheKey("hello"), parameter.createCacheKey("hello"));
        assertNotEquals(parameter.createCacheKey("hello"), parameter.createCacheKey("world"));
    }

    @Test
    @DisplayName("toJsonKey keeps the Redis key format byte-identical (golden)")
    void jsonKeyGolden() {
        String json = new ChatCacheParameter("gpt-4o", 42, 0.0).createCacheKey("hello").toJsonKey();
        String expected = """
                {
                  "model" : "gpt-4o",
                  "seed" : 42,
                  "temperature" : 0.0,
                  "mode" : "CHAT",
                  "content" : "hello"
                }""";
        assertEquals(expected, json.replace("\r\n", "\n"));
    }

    @Test
    @DisplayName("toJsonKey is equal for Bifrost and OpenAI models of the same name")
    void jsonKeyEqualForBifrostAndOpenAi() {
        EnvironmentProvider environment = new MapEnvironment(Map.of());
        ChatModelProvider bifrost = new ChatModelProvider(LlmConfiguration.builder(ChatModelPlatform.BIFROST)
                .modelName("openai/gpt-4o")
                .seed(42)
                .environment(environment)
                .build());
        ChatModelProvider openAi = new ChatModelProvider(LlmConfiguration.builder(ChatModelPlatform.OPENAI)
                .modelName("gpt-4o")
                .seed(42)
                .environment(environment)
                .build());

        assertEquals(openAi.cacheParameters().createCacheKey("hello").toJsonKey(), bifrost.cacheParameters().createCacheKey("hello").toJsonKey());
    }
}
