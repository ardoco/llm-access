/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
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
 * Requests without tool specifications are stored as plain text under the string representation of their
 * messages. Requests with tool specifications (e.g., from {@code AiServices} with {@code @Tool} methods) are
 * stored under that same string followed by {@code "\nTools: "} and the string representation of the tool
 * specifications sorted by name (so the reflection order of tool methods does not matter). Their value is a
 * {@link CachedAiMessage}, which keeps the text and the tool execution requests (id, name, arguments); a hit
 * replays them with the same ids, so the follow-up request carrying the tool results is a hit as well. Other
 * request parameters (tool choice, response format, ...) are not part of the key.
 * <p>
 * Cached tool replies keep text, tool calls, thinking, attributes and finish reason. Cache hits carry no token
 * usage or other response metadata.
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
     * miss the request is forwarded to the delegate, and the result is cached and flushed. Requests without tool
     * specifications cache only the resulting text; requests with tool specifications cache a
     * {@link CachedAiMessage} (text, tool calls, thinking, attributes and finish reason).
     */
    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        List<ToolSpecification> tools = chatRequest.toolSpecifications();
        if (tools == null || tools.isEmpty()) {
            return doChatWithoutTools(chatRequest);
        }
        return doChatWithTools(chatRequest, tools);
    }

    private ChatResponse doChatWithoutTools(ChatRequest chatRequest) {
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

    private ChatResponse doChatWithTools(ChatRequest chatRequest, List<ToolSpecification> tools) {
        String key = toolKey(chatRequest, tools);
        CachedAiMessage cached = cache.get(key, CachedAiMessage.class);
        if (cached != null) {
            AiMessage message = cached.toAiMessage();
            FinishReason finishReason = cached.finishReason();
            if (finishReason == null) {
                finishReason = message.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP;
            }
            return ChatResponse.builder().aiMessage(message).finishReason(finishReason).build();
        }
        ChatResponse response = delegate.doChat(chatRequest);
        cache.put(key, CachedAiMessage.from(response.aiMessage(), response.finishReason()));
        cache.flush();
        return response;
    }

    private static String toolKey(ChatRequest chatRequest, List<ToolSpecification> tools) {
        List<ToolSpecification> sorted = tools.stream().sorted(Comparator.comparing(ToolSpecification::name)).toList();
        return chatRequest.messages() + "\nTools: " + sorted;
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
