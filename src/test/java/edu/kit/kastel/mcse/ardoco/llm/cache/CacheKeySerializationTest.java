/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.cache.embedding.EmbeddingCacheParameter;

/**
 * Pins the exact serialized form of cache keys. The JSON key addresses entries in Redis, so any change (property order,
 * escaping, whitespace) invalidates all existing caches.
 */
@NullMarked
class CacheKeySerializationTest {

    @Test
    @DisplayName("chat key JSON is byte-stable")
    void chatKey() {
        String json = new ChatCacheParameter("gpt-4o/mini", 42, 0.5).createCacheKey("a/b\\c \"q\" ä€ 😀 <>&'\n\t\u0001").toJsonKey();
        String expected = "{\n  \"model\" : \"gpt-4o/mini\",\n  \"seed\" : 42,\n  \"temperature\" : 0.5,\n  \"mode\" : \"CHAT\",\n" + "  \"content\" : \"a/b\\\\c \\\"q\\\" ä€ 😀 <>&'\\n\\t\\u0001\"\n}";
        assertEquals(expected, json);
    }

    @Test
    @DisplayName("embedding key JSON is byte-stable")
    void embeddingKey() {
        String json = new EmbeddingCacheParameter("text-embedding/3").createCacheKey("hello").toJsonKey();
        String expected = "{\n  \"model\" : \"text-embedding/3\",\n  \"seed\" : -1,\n  \"temperature\" : -1.0,\n  \"mode\" : \"EMBEDDING\",\n" + "  \"content\" : \"hello\"\n}";
        assertEquals(expected, json);
    }
}
