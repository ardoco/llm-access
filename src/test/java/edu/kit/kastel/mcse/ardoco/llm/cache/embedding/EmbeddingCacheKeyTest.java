/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache.embedding;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.kit.kastel.mcse.ardoco.llm.util.KeyGenerator;

/**
 * Tests for {@link EmbeddingCacheParameter} and {@link EmbeddingCacheKey}.
 */
@NullMarked
class EmbeddingCacheKeyTest {

    @Test
    @DisplayName("parameters() is the bare model name")
    void parametersAreModelName() {
        assertEquals("text-embedding-ada-002", new EmbeddingCacheParameter("text-embedding-ada-002").parameters());
    }

    @Test
    @DisplayName("createCacheKey derives the local key from the content and marks the EMBEDDING mode")
    void createCacheKey() {
        EmbeddingCacheKey key = new EmbeddingCacheParameter("ada").createCacheKey("hello");
        assertEquals("ada", key.model());
        assertEquals("hello", key.content());
        assertEquals(KeyGenerator.generateKey("hello"), key.localKey());
    }

    @Test
    @DisplayName("toJsonKey serializes the EMBEDDING mode but not the local key")
    void jsonKeyExcludesLocalKey() {
        EmbeddingCacheKey key = new EmbeddingCacheParameter("ada").createCacheKey("hello");
        String json = key.toJsonKey();
        assertTrue(json.contains("EMBEDDING"), json);
        assertTrue(json.contains("hello"), json);
        assertFalse(json.contains(key.localKey()), "local key must be excluded from the JSON key");
    }

    @Test
    @DisplayName("ofRaw keeps a custom local key for backward compatibility")
    void ofRawCustomLocalKey() {
        @SuppressWarnings("deprecation") EmbeddingCacheKey key = EmbeddingCacheKey.ofRaw("ada", "content", "custom-local-key");
        assertEquals("custom-local-key", key.localKey());
        assertEquals("ada", key.model());
    }

    @Test
    @DisplayName("keys are equal for the same parameter and content, distinct otherwise")
    void equality() {
        EmbeddingCacheParameter parameter = new EmbeddingCacheParameter("ada");
        assertEquals(parameter.createCacheKey("hello"), parameter.createCacheKey("hello"));
        assertNotEquals(parameter.createCacheKey("hello"), parameter.createCacheKey("world"));
    }
}
