/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * Tests for {@link CachingChatModel} using a real local cache and a recording delegate model.
 * <p>
 * The delegate only implements {@link ChatModel#doChat(ChatRequest)}, the single method every
 * {@code chat(...)} overload funnels through. The tests therefore do not depend on which overload the caching
 * decorator happens to call internally, and every public entry point is exercised.
 */
@NullMarked
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CachingChatModelTest {

    @TempDir
    private Path tempCacheDir;

    @BeforeAll
    void init() {
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
    @DisplayName("string chat is computed once and then served from the cache")
    void cachesStringChat() {
        RecordingChatModel delegate = new RecordingChatModel();
        CachingChatModel model = new CachingChatModel(delegate, cache());

        String first = model.chat("hello");
        String second = model.chat("hello");

        assertEquals(first, second);
        assertEquals(1, delegate.calls, "the second call must hit the cache");
    }

    @Test
    @DisplayName("message-list chat is computed once and then served from the cache")
    void cachesMessageListChat() {
        RecordingChatModel delegate = new RecordingChatModel();
        CachingChatModel model = new CachingChatModel(delegate, cache());
        List<ChatMessage> messages = List.of(UserMessage.from("hi"));

        String first = model.chat(messages).aiMessage().text();
        String second = model.chat(messages).aiMessage().text();

        assertEquals(first, second);
        assertEquals(1, delegate.calls, "the second call must hit the cache");
    }

    @Test
    @DisplayName("varargs chat is computed once and then served from the cache")
    void cachesVarargsChat() {
        RecordingChatModel delegate = new RecordingChatModel();
        CachingChatModel model = new CachingChatModel(delegate, cache());

        String first = model.chat(UserMessage.from("hey")).aiMessage().text();
        String second = model.chat(UserMessage.from("hey")).aiMessage().text();

        assertEquals(first, second);
        assertEquals(1, delegate.calls, "the second call must hit the cache");
    }

    @Test
    @DisplayName("request-based chat is computed once and then served from the cache")
    void cachesRequestChat() {
        RecordingChatModel delegate = new RecordingChatModel();
        CachingChatModel model = new CachingChatModel(delegate, cache());
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("yo")).build();

        String first = model.chat(request).aiMessage().text();
        String second = model.chat(request).aiMessage().text();

        assertEquals(first, second);
        assertEquals(1, delegate.calls, "the second call must hit the cache");
    }

    @Test
    @DisplayName("cached responses survive a cache reload from disk")
    void cachePersistsAcrossReload() throws IOException {
        RecordingChatModel delegate = new RecordingChatModel();
        String expected = new CachingChatModel(delegate, cache()).chat("ping");
        assertEquals(1, delegate.calls);

        // Recreate the manager so the cache is read fresh from disk instead of from memory.
        CacheManager.setCacheDir(tempCacheDir.toString());

        RecordingChatModel reloadedDelegate = new RecordingChatModel();
        String afterReload = new CachingChatModel(reloadedDelegate, cache()).chat("ping");

        assertEquals(expected, afterReload);
        assertEquals(0, reloadedDelegate.calls, "the reloaded cache must serve the value without touching the delegate");
    }

    /**
     * A minimal {@link ChatModel} test double that records how often it produced a response and echoes the
     * request so that distinct prompts map to distinct answers. Implementing only
     * {@link #doChat(ChatRequest)} is sufficient because every {@code chat(...)} overload delegates to it.
     */
    private static final class RecordingChatModel implements ChatModel {

        private int calls;

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            calls++;
            return ChatResponse.builder().aiMessage(AiMessage.from("reply:" + chatRequest.messages())).build();
        }
    }
}
