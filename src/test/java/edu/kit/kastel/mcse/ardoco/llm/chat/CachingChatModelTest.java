/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.ClearLocalCachesExtension;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.KeyGenerator;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;

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
    private final EnvironmentProvider environment = new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR"));

    @TempDir
    private Path tempCacheDir;

    @BeforeEach
    void setup() throws IOException {
        CacheManager.setCacheDir(tempCacheDir.toString(), environment);
    }

    private Cache<ChatCacheKey> cache() {
        return CacheManager.getDefaultInstance().getCache(new ChatCacheParameter("test-model", 42, 0.0));
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

        CacheManager.getDefaultInstance().flush();
        ClearLocalCachesExtension.clearInstances();
        // Recreate the manager so the cache is read fresh from disk instead of from memory.
        CacheManager.setCacheDir(tempCacheDir.toString(), environment);

        RecordingChatModel reloadedDelegate = new RecordingChatModel();
        String afterReload = new CachingChatModel(reloadedDelegate, cache()).chat("ping");

        assertEquals(expected, afterReload);
        assertEquals(0, reloadedDelegate.calls, "the reloaded cache must serve the value without touching the delegate");
    }

    @Test
    @DisplayName("a Bifrost model is served from an entry written through OpenAI parameters")
    void bifrostSharesCacheWithOpenAi() throws IOException {
        ChatModelProvider openAi = new ChatModelProvider(LlmConfiguration.builder(ChatModelPlatform.OPENAI)
                .modelName("gpt-4o-mini")
                .environment(new MapEnvironment(Map.of("OPENAI_API_KEY", "DUMMY", "OPENAI_ORGANIZATION_ID", "DUMMY")))
                .build());
        RecordingChatModel delegate = new RecordingChatModel();
        CacheManager writer = new CacheManager(tempCacheDir, environment);
        String expected = new CachingChatModel(delegate, writer.getCache(openAi.cacheParameters())).chat("ping");
        writer.flush();
        ClearLocalCachesExtension.clearInstances();

        ChatModelProvider bifrost = new ChatModelProvider(LlmConfiguration.builder(ChatModelPlatform.BIFROST)
                .modelName("openai/gpt-4o-mini")
                .environment(new MapEnvironment(Map.of("BIFROST_API_KEY", "DUMMY", "BIFROST_URL", "http://localhost:1/v1")))
                .build());
        CacheManager reader = new CacheManager(tempCacheDir, environment);
        // The lazy model never connects to the gateway as long as the cache answers.
        String actual = new CachingChatModel(bifrost.createChatModel(), reader.getCache(bifrost.cacheParameters())).chat("ping");

        assertEquals(expected, actual);
        assertEquals(1, delegate.calls);
    }

    private static final ToolSpecification TOOL_A = ToolSpecification.builder().name("alpha").description("first").build();
    private static final ToolSpecification TOOL_B = ToolSpecification.builder().name("beta").description("second").build();
    private static final ToolExecutionRequest CALL = ToolExecutionRequest.builder().id("call-1").name("alpha").arguments("{\"x\":1}").build();

    private static final ToolExecutionRequest TOOL_LOOP_CALL = ToolExecutionRequest.builder().id("call-2").name("alpha").arguments("{\"arg0\":1}").build();

    private ChatRequest toolRequest(ToolSpecification... tools) {
        return ChatRequest.builder().messages(UserMessage.from("use a tool")).toolSpecifications(tools).build();
    }

    @Test
    @DisplayName("a tool call reply is replayed from the cache without calling the delegate")
    void cachesToolCalls() {
        ScriptedChatModel delegate = new ScriptedChatModel(AiMessage.from(CALL));
        CachingChatModel model = new CachingChatModel(delegate, cache());

        ChatResponse first = model.chat(toolRequest(TOOL_A));
        ChatResponse second = model.chat(toolRequest(TOOL_A));

        assertEquals(1, delegate.calls);
        assertEquals(List.of(CALL), first.aiMessage().toolExecutionRequests());
        assertEquals(List.of(CALL), second.aiMessage().toolExecutionRequests());
        assertNull(second.aiMessage().text());
    }

    @Test
    @DisplayName("different tool sets do not share cache entries")
    void toolSetsDoNotCollide() {
        ScriptedChatModel delegate = new ScriptedChatModel(AiMessage.from("one"), AiMessage.from("two"), AiMessage.from("three"));
        CachingChatModel model = new CachingChatModel(delegate, cache());

        model.chat(toolRequest(TOOL_A));
        model.chat(toolRequest(TOOL_A, TOOL_B));
        model.chat(ChatRequest.builder().messages(UserMessage.from("use a tool")).build());

        assertEquals(3, delegate.calls);
    }

    @Test
    @DisplayName("tool order does not influence the cache key")
    void toolOrderIsIrrelevant() {
        ScriptedChatModel delegate = new ScriptedChatModel(AiMessage.from("one"), AiMessage.from("two"));
        CachingChatModel model = new CachingChatModel(delegate, cache());

        String first = model.chat(toolRequest(TOOL_A, TOOL_B)).aiMessage().text();
        String second = model.chat(toolRequest(TOOL_B, TOOL_A)).aiMessage().text();

        assertEquals(1, delegate.calls);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("requests without tools still store plain text")
    void withoutToolsStoresPlainText() {
        Cache<ChatCacheKey> cache = cache();
        ScriptedChatModel delegate = new ScriptedChatModel(AiMessage.from("plain"));
        CachingChatModel model = new CachingChatModel(delegate, cache);
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

        model.chat(request);

        assertEquals("plain", cache.get(request.messages().toString(), String.class));
    }

    @Test
    @DisplayName("tool-free requests hit a hand-written cache file in the pre-change format and leave it plain text")
    void toolFreeRequestHitsExistingCacheFile() throws IOException {
        ChatRequest request = ChatRequest.builder().messages(UserMessage.from("legacy question")).build();
        // Pre-change on-disk format, built by hand: {"<generateKey(messages.toString())>":"<JSON-quoted plain text>"}
        String legacyFile = "{\"" + KeyGenerator.generateKey(request.messages().toString()) + "\":\"\\\"legacy answer\\\"\"}";
        Path cacheFile = tempCacheDir.resolve("Chat_test-model_42.json");
        Files.writeString(cacheFile, legacyFile);
        ClearLocalCachesExtension.clearInstances();
        CacheManager.setCacheDir(tempCacheDir.toString(), environment);

        ScriptedChatModel delegate = new ScriptedChatModel();
        String answer = new CachingChatModel(delegate, cache()).chat(request).aiMessage().text();

        assertEquals("legacy answer", answer);
        assertEquals(0, delegate.calls, "the legacy entry must be hit without contacting the delegate");
        CacheManager.getDefaultInstance().flush();
        assertEquals(legacyFile, Files.readString(cacheFile), "the cache file must stay untouched");
    }

    @Test
    @DisplayName("an AiServices tool loop is fully served from a reloaded cache")
    void aiServicesToolLoopIsCached() throws IOException {
        ScriptedChatModel firstDelegate = new ScriptedChatModel(AiMessage.from(TOOL_LOOP_CALL), AiMessage.from("The answer is 2"));
        CalculatorTool firstTool = new CalculatorTool();
        String first = assistant(firstDelegate, firstTool).chat("what is 1+1?");

        assertEquals("The answer is 2", first);
        assertEquals(2, firstDelegate.calls);
        assertEquals(1, firstTool.invocations);

        CacheManager.getDefaultInstance().flush();
        ClearLocalCachesExtension.clearInstances();
        CacheManager.setCacheDir(tempCacheDir.toString(), environment);

        ScriptedChatModel secondDelegate = new ScriptedChatModel();
        String second = assistant(secondDelegate, new CalculatorTool()).chat("what is 1+1?");

        assertEquals(first, second);
        assertEquals(0, secondDelegate.calls, "both model calls must be served from the reloaded cache");
    }

    private Assistant assistant(ChatModel delegate, CalculatorTool tool) {
        return AiServices.builder(Assistant.class).chatModel(new CachingChatModel(delegate, cache())).tools(tool).build();
    }

    interface Assistant {
        String chat(String question);
    }

    static final class CalculatorTool {
        private int invocations;

        @Tool("Increments a number")
        String alpha(@P("the number") int x) {
            invocations++;
            return String.valueOf(x + 1);
        }
    }

    /** Replies with the given messages in order. */
    private static final class ScriptedChatModel implements ChatModel {
        private final List<AiMessage> replies;
        private int calls;

        private ScriptedChatModel(AiMessage... replies) {
            this.replies = new ArrayList<>(List.of(replies));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(replies.get(calls++)).build();
        }
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
