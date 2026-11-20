/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;

/**
 * Tests {@link ChatModelProvider}: exposed settings and the {@link ChatModelProvider#createChatModel()}
 * factory, including its fail-fast behaviour when required credentials are missing. Credentials are injected
 * through a {@link MapEnvironment}, so the results do not depend on the developer's {@code .env} or system
 * environment.
 */
@NullMarked
class ChatModelProviderTest {

    private final EnvironmentProvider openAiEnvironment = new MapEnvironment(Map.of("OPENAI_API_KEY", "DUMMY", "OPENAI_ORGANIZATION_ID", "DUMMY"));
    private final EnvironmentProvider emptyEnvironment = new MapEnvironment(Map.of());

    @Test
    @DisplayName("provider exposes the configured settings")
    void exposesSettings() {
        LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OLLAMA)
                .modelName("mistral")
                .seed(99)
                .temperature(0.7)
                .environment(emptyEnvironment)
                .build();
        ChatModelProvider provider = new ChatModelProvider(config);

        assertEquals(ChatModelPlatform.OLLAMA, provider.platform());
        assertEquals("mistral", provider.modelName());
        assertEquals(99, provider.seed());
        assertEquals(0.7, provider.temperature());
        assertEquals(new ChatCacheParameter("mistral", 99, 0.7), provider.cacheParameters());
    }

    @Test
    @DisplayName("Bifrost and OpenAI share the cache parameters, while modelName keeps the prefix")
    void bifrostCacheParametersIgnoreProviderPrefix() {
        ChatModelProvider bifrost = new ChatModelProvider(configuration(ChatModelPlatform.BIFROST, "openai/gpt-4o-mini", emptyEnvironment));
        ChatModelProvider openAi = new ChatModelProvider(configuration(ChatModelPlatform.OPENAI, "gpt-4o-mini", emptyEnvironment));

        assertEquals(openAi.cacheParameters(), bifrost.cacheParameters());
        assertEquals("gpt-4o-mini", bifrost.cacheParameters().modelName());
        assertEquals("openai/gpt-4o-mini", bifrost.modelName());
    }

    @Test
    @DisplayName("Open WebUI model names are not stripped")
    void openWebUiCacheParametersKeepName() {
        ChatModelProvider provider = new ChatModelProvider(configuration(ChatModelPlatform.OPENWEBUI, "meta-llama/x", emptyEnvironment));
        assertEquals("meta-llama/x", provider.cacheParameters().modelName());
    }

    @Test
    @DisplayName("createChatModel builds a lazy model when the required credentials are present")
    void buildsOpenAiModel() {
        ChatModel model = new ChatModelProvider(configuration(ChatModelPlatform.OPENAI, "gpt-4o-mini", openAiEnvironment)).createChatModel();
        assertNotNull(model);
        assertInstanceOf(LazyChatModel.class, model);
    }

    @Test
    @DisplayName("createChatModel builds a lazy Bifrost model with API key and URL")
    void buildsBifrostModel() {
        EnvironmentProvider environment = new MapEnvironment(Map.of("BIFROST_API_KEY", "DUMMY", "BIFROST_URL", "http://localhost:1/v1"));
        ChatModel model = new ChatModelProvider(configuration(ChatModelPlatform.BIFROST, "openai/gpt-4o-mini", environment)).createChatModel();
        assertInstanceOf(LazyChatModel.class, model);
    }

    @Test
    @DisplayName("createChatModel fails fast for Bifrost when only the key or only the URL is set")
    void bifrostRequiresKeyAndUrl() {
        for (Map<String, String> variables : List.of(Map.of("BIFROST_API_KEY", "DUMMY"), Map.of("BIFROST_URL", "http://localhost:1/v1"))) {
            ChatModelProvider provider = new ChatModelProvider(configuration(ChatModelPlatform.BIFROST, "openai/gpt-4o-mini", new MapEnvironment(variables)));
            assertThrows(IllegalStateException.class, provider::createChatModel);
        }
    }

    @Test
    @DisplayName("createChatModel fails fast when required credentials are missing")
    void missingCredentialsThrow() {
        for (ChatModelPlatform platform : ChatModelPlatform.values()) {
            ChatModelProvider provider = new ChatModelProvider(configuration(platform, "model", emptyEnvironment));
            assertThrows(IllegalStateException.class, provider::createChatModel, () -> platform + " must require credentials");
        }
    }

    @Test
    @DisplayName("credentials are read from the injected environment only")
    void usesInjectedEnvironment() {
        EnvironmentProvider ollamaEnvironment = new MapEnvironment(Map.of("OLLAMA_HOST", "http://localhost:11434"));
        assertInstanceOf(LazyChatModel.class, new ChatModelProvider(configuration(ChatModelPlatform.OLLAMA, "mistral", ollamaEnvironment)).createChatModel());
        assertThrows(IllegalStateException.class, () -> new ChatModelProvider(configuration(ChatModelPlatform.OPENAI, "gpt-4o-mini", ollamaEnvironment))
                .createChatModel());
    }

    private LlmConfiguration configuration(ChatModelPlatform platform, String modelName, EnvironmentProvider environment) {
        return LlmConfiguration.builder(platform).modelName(modelName).environment(environment).build();
    }
}
