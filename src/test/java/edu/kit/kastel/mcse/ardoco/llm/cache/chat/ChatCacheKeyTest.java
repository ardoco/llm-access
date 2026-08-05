/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.kit.kastel.mcse.ardoco.llm.util.KeyGenerator;

/**
 * Tests for {@link ChatCacheParameter} and {@link ChatCacheKey}, including the backward-compatible file
 * identifier and JSON key format.
 */
@NullMarked
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
}
