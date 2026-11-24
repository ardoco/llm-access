/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

/**
 * A Jackson-serializable snapshot of an {@link AiMessage} with tool calls, as stored by {@link CachingChatModel}.
 * Own records are needed because {@link ToolExecutionRequest} cannot be deserialized by Jackson.
 *
 * @param text       The text of the message, may be {@code null} for tool-call-only replies
 * @param toolCalls  The requested tool executions
 * @param thinking   The thinking/reasoning content, may be {@code null}
 * @param attributes The provider-specific attributes; a missing value is treated as empty
 */
record CachedAiMessage(@Nullable String text, List<CachedToolCall> toolCalls, @Nullable String thinking, Map<String, Object> attributes) {

    CachedAiMessage {
        if (attributes == null) {
            attributes = Map.of();
        }
    }

    /**
     * A cached {@link ToolExecutionRequest}.
     *
     * @param id        The id of the tool call
     * @param name      The tool name
     * @param arguments The arguments as JSON string
     */
    record CachedToolCall(@Nullable String id, @Nullable String name, @Nullable String arguments) {
    }

    static CachedAiMessage from(AiMessage message) {
        List<CachedToolCall> calls = message.toolExecutionRequests().stream().map(r -> new CachedToolCall(r.id(), r.name(), r.arguments())).toList();
        return new CachedAiMessage(message.text(), calls, message.thinking(), message.attributes());
    }

    AiMessage toAiMessage() {
        List<ToolExecutionRequest> requests = toolCalls.stream()
                .map(c -> ToolExecutionRequest.builder().id(c.id()).name(c.name()).arguments(c.arguments()).build())
                .toList();
        return AiMessage.builder().text(text).thinking(thinking).toolExecutionRequests(requests).attributes(attributes).build();
    }
}
