/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.model.chat.ChatModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * Tests for {@link ChatModelUtils} caching behavior using a real local cache and a mocked chat model.
 */
@NullMarked
class ChatModelUtilsTest {

    @TempDir
    private Path tempCacheDir;

    @BeforeAll
    static void init() {
        Environment.overwrite(Path.of("src/test/resources/.env-test"));
    }

    @BeforeEach
    void setup() throws IOException {
        CacheManager.setCacheDir(tempCacheDir.toString());
    }

    private Cache<ChatCacheKey> cache() {
        return CacheManager.getDefaultInstance().getCache(this, new ChatCacheParameter("test-model", 42, 0.0));
    }

    @Test
    @DisplayName("cachedRequest queries the model on a miss and serves the second call from cache")
    void testCachedRequestCaches() {
        ChatModel llm = mock(ChatModel.class);
        when(llm.chat("hello")).thenReturn("world");
        Cache<ChatCacheKey> cache = cache();

        assertEquals("world", ChatModelUtils.cachedRequest("hello", llm, cache));
        assertEquals("world", ChatModelUtils.cachedRequest("hello", llm, cache));

        verify(llm, times(1)).chat("hello");
    }

    @Test
    @DisplayName("nCachedRequest issues n requests, then serves from cache")
    void testNCachedRequest() {
        ChatModel llm = mock(ChatModel.class);
        when(llm.chat("prompt")).thenReturn("answer");
        Cache<ChatCacheKey> cache = cache();

        List<String> first = ChatModelUtils.nCachedRequest("prompt", llm, cache, 3);
        assertEquals(List.of("answer", "answer", "answer"), first);
        verify(llm, times(3)).chat("prompt");

        List<String> second = ChatModelUtils.nCachedRequest("prompt", llm, cache, 3);
        assertEquals(first, second);
        // No further model calls: still exactly 3 in total
        verify(llm, times(3)).chat("prompt");
    }

    @Test
    @DisplayName("cached responses survive a cache reload from disk")
    void testCachePersistence() throws IOException {
        ChatModel llm = mock(ChatModel.class);
        when(llm.chat("persist")).thenReturn("stored");

        ChatModelUtils.cachedRequest("persist", llm, cache());
        CacheManager.getDefaultInstance().flush();

        // Fresh manager reading the same directory must return the cached value without a model call
        CacheManager.setCacheDir(tempCacheDir.toString());
        ChatModel llm2 = mock(ChatModel.class);
        assertEquals("stored", ChatModelUtils.cachedRequest("persist", llm2, cache()));
        verifyNoInteractions(llm2);
    }

    @Test
    @DisplayName("nCachedRequest rejects non-positive counts")
    void testInvalidCount() {
        ChatModel llm = mock(ChatModel.class);
        Cache<ChatCacheKey> cache = cache();
        assertThrows(IllegalArgumentException.class, () -> ChatModelUtils.nCachedRequest("x", llm, cache, 0));
    }
}
