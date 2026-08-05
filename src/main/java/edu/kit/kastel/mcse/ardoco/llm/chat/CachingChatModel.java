/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;

/**
 * A {@link ChatModel} decorator that transparently caches responses in a {@link Cache}, so that repeated
 * prompts are not re-sent to the underlying model.
 * <p>
 * All {@code chat(...)} overloads of {@link ChatModel} funnel through {@link #doChat(ChatRequest)}, so caching
 * is implemented once at that single point: every entry point (string, message list, varargs or
 * {@link ChatRequest}) is cached consistently. Requests are cached by the string representation of their
 * messages (the cache normalizes line endings). The remaining, non-caching methods (parameters, listeners,
 * provider, capabilities) are delegated to the wrapped model.
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

    /**
     * The single point through which all {@link ChatModel#chat} overloads are routed by the default
     * interface methods. On a cache hit the stored response is returned without contacting the delegate; on a
     * miss the request is forwarded to the delegate and the resulting text is cached and flushed.
     */
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String key = chatRequest.messages().toString();
        String cached = cache.get(key, String.class);
        if (cached != null) {
            return ChatResponse.builder().aiMessage(AiMessage.from(cached)).build();
        }
        ChatResponse response = delegate.doChat(chatRequest);
        cache.put(key, response.aiMessage().text());
        cache.flush();
        return response;
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
