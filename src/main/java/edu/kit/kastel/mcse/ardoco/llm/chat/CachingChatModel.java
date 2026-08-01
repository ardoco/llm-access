/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;

/**
 * A {@link ChatModel} decorator that transparently caches responses in a {@link Cache}, so that repeated
 * prompts are not re-sent to the underlying model. Message-based chats are cached by their string
 * representation (the cache normalizes line endings). All non-caching methods are delegated to the wrapped
 * model.
 * <p>
 * After each newly computed response the cache is flushed, so responses are persisted immediately (relevant
 * for the file-based cache).
 */
public final class CachingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Cache<ChatCacheKey> cache;

    /**
     * Creates a caching chat model.
     *
     * @param delegate The underlying chat model to which cache misses are delegated
     * @param cache    The cache used to store and retrieve responses
     */
    public CachingChatModel(ChatModel delegate, Cache<ChatCacheKey> cache) {
        this.delegate = Objects.requireNonNull(delegate);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        String key = messages.toString();
        String cached = cache.get(key, String.class);
        if (cached != null) {
            return ChatResponse.builder().aiMessage(AiMessage.from(cached)).build();
        }
        ChatResponse response = delegate.chat(messages);
        cache.put(key, response.aiMessage().text());
        cache.flush();
        return response;
    }

    @Override
    public ChatResponse chat(ChatMessage... messages) {
        return chat(List.of(messages));
    }

    @Override
    public String chat(String userMessage) {
        String cached = cache.get(userMessage, String.class);
        if (cached != null) {
            return cached;
        }
        String response = delegate.chat(userMessage);
        cache.put(userMessage, response);
        cache.flush();
        return response;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return delegate.chat(chatRequest);
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {
        return delegate.chat(chatRequest, options);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return delegate.doChat(chatRequest);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
