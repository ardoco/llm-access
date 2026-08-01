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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * Tests for {@link CachingChatModel} using a real local cache and a mocked delegate model.
 */
@NullMarked
class CachingChatModelTest {

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
    @DisplayName("message-based chat is cached and served from cache on the second call")
    void testMessageCaching() {
        List<ChatMessage> messages = List.of(UserMessage.from("hello"));
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.chat(messages)).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("world")).build());

        CachingChatModel model = new CachingChatModel(delegate, cache());

        assertEquals("world", model.chat(messages).aiMessage().text());
        assertEquals("world", model.chat(messages).aiMessage().text());

        verify(delegate, times(1)).chat(messages);
    }

    @Test
    @DisplayName("string chat is cached and survives a cache reload from disk")
    void testStringCachingPersists() {
        ChatModel delegate = mock(ChatModel.class);
        when(delegate.chat("ping")).thenReturn("pong");

        assertEquals("pong", new CachingChatModel(delegate, cache()).chat("ping"));

        // Fresh manager reading the same directory must serve the cached value without touching the delegate
        ChatModel delegate2 = mock(ChatModel.class);
        assertEquals("pong", new CachingChatModel(delegate2, cache()).chat("ping"));
        verifyNoInteractions(delegate2);
    }
}
