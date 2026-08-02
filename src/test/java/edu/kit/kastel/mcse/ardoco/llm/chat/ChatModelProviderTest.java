/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * Tests {@link ChatModelProvider}: exposed settings and the {@link ChatModelProvider#createChatModel()}
 * factory, including its fail-fast behaviour when required credentials are missing.
 */
@NullMarked
class ChatModelProviderTest {

    @BeforeAll
    static void init() {
        Environment.overwrite(Path.of("src/test/resources/.env-test"));
    }

    @Test
    @DisplayName("provider exposes the configured settings")
    void exposesSettings() {
        LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OLLAMA).modelName("mistral").seed(99).temperature(0.7).build();
        ChatModelProvider provider = new ChatModelProvider(config);

        assertEquals(ChatModelPlatform.OLLAMA, provider.platform());
        assertEquals("mistral", provider.modelName());
        assertEquals(99, provider.seed());
        assertEquals(0.7, provider.temperature());
        assertEquals(new ChatCacheParameter("mistral", 99, 0.7), provider.cacheParameters());
    }

    @Test
    @DisplayName("createChatModel builds a lazy model when the required credentials are present")
    void buildsOpenAiModel() {
        // .env-test provides OPENAI_API_KEY
        ChatModel model = new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.OPENAI, "gpt-4o-mini")).createChatModel();
        assertNotNull(model);
        assertTrue(model instanceof LazyChatModel);
    }

    @Test
    @DisplayName("createChatModel fails fast when required credentials are missing")
    void missingCredentialsThrow() {
        // .env-test defines no Blablador/DeepSeek/Open WebUI credentials
        assertThrows(IllegalStateException.class, () -> new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.BLABLADOR, "llama")).createChatModel());
        assertThrows(IllegalStateException.class, () -> new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.DEEPSEEK, "deepseek-chat"))
                .createChatModel());
        assertThrows(IllegalStateException.class, () -> new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.OPENWEBUI, "llama")).createChatModel());
    }
}
