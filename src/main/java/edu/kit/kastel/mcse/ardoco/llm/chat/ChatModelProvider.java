/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;

/**
 * Provides chat language model instances for different platforms.
 * This class supports multiple language model platforms (OpenAI, Ollama, Blablador, DeepSeek, Open WebUI)
 * and handles their configuration, including authentication and model settings.
 * <p>
 * Model settings are supplied through a framework-neutral {@link LlmConfiguration}, while credentials and
 * host URLs are read from the {@link EnvironmentProvider} carried by that configuration.
 * <p>
 * Required environment variables for each platform:
 * <ul>
 * <li>OpenAI:
 * <ul>
 * <li>{@code OPENAI_API_KEY}: Your OpenAI API key</li>
 * <li>{@code OPENAI_ORGANIZATION_ID}: Your OpenAI organization ID (optional; sent only when set)</li>
 * </ul>
 * </li>
 * <li>Ollama:
 * <ul>
 * <li>{@code OLLAMA_HOST}: The host URL for the Ollama server</li>
 * <li>{@code OLLAMA_USER}: Username for Ollama basic authentication (optional)</li>
 * <li>{@code OLLAMA_PASSWORD}: Password for Ollama basic authentication (optional)</li>
 * <li>{@code OLLAMA_TOKEN}: Token for an OpenAI-compatible endpoint at the Ollama host (optional; used
 * when no user/password is set)</li>
 * </ul>
 * </li>
 * <li>Blablador:
 * <ul>
 * <li>{@code BLABLADOR_API_KEY}: Your Blablador API key</li>
 * </ul>
 * </li>
 * <li>DeepSeek:
 * <ul>
 * <li>{@code DEEPSEEK_API_KEY}: Your DeepSeek API key</li>
 * </ul>
 * </li>
 * <li>Open WebUI:
 * <ul>
 * <li>{@code OPENWEBUI_URL}: The URL for the Open WebUI server</li>
 * <li>{@code OPENWEBUI_API_KEY}: Your Open WebUI API key</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @see ChatModelPlatform
 * @see LlmConfiguration
 */
public class ChatModelProvider {

    /**
     * The platform to use for the language model.
     */
    private final ChatModelPlatform platform;

    /**
     * The name of the model to use.
     */
    private final String modelName;

    /**
     * The seed value for model randomization.
     */
    private final int seed;

    /**
     * Temperature setting for the model.
     */
    private final double temperature;

    /**
     * The environment that supplies credentials and host URLs.
     */
    private final EnvironmentProvider environment;

    /**
     * Creates a new chat language model provider with the specified configuration.
     *
     * @param configuration The configuration containing model settings
     */
    public ChatModelProvider(LlmConfiguration configuration) {
        Objects.requireNonNull(configuration);
        this.platform = configuration.platform();
        this.modelName = configuration.modelName();
        this.seed = configuration.seed();
        this.temperature = configuration.temperature();
        this.environment = configuration.environment();
    }

    /**
     * Creates a chat model instance based on the configured platform.
     *
     * @return A chat model instance for the configured platform
     * @throws IllegalArgumentException If the platform is not supported
     */
    public ChatModel createChatModel() {
        return switch (platform) {
            case OPENAI -> createOpenAiChatModel();
            case OLLAMA -> createOllamaChatModel();
            case BLABLADOR -> createBlabladorChatModel();
            case DEEPSEEK -> createDeepSeekChatModel();
            case OPENWEBUI -> createOpenWebUIChatModel();
        };
    }

    /**
     * Gets the platform used by this provider.
     *
     * @return The platform
     */
    public ChatModelPlatform platform() {
        return platform;
    }

    /**
     * Gets the name of the configured model.
     *
     * @return The model name
     */
    public String modelName() {
        return modelName;
    }

    /**
     * Gets the seed value used for model randomization.
     *
     * @return The seed value
     */
    public int seed() {
        return seed;
    }

    /**
     * Gets the temperature setting for the model.
     *
     * @return The temperature value
     */
    public double temperature() {
        return temperature;
    }

    /**
     * Creates an Ollama chat model instance.
     * The model is configured with basic authentication if user and password are provided, or uses an
     * OpenAI-compatible endpoint at the Ollama host if only a token is provided.
     *
     * @return A configured Ollama chat model instance
     * @throws IllegalStateException If the {@code OLLAMA_HOST} environment variable is not set
     */
    private ChatModel createOllamaChatModel() {
        String host = environment.getenvNonNull("OLLAMA_HOST");
        String user = environment.getenv("OLLAMA_USER");
        String password = environment.getenv("OLLAMA_PASSWORD");
        String token = environment.getenv("OLLAMA_TOKEN");

        return new LazyChatModel(() -> {
            boolean hasBasicAuth = user != null && password != null && !user.isEmpty() && !password.isEmpty();
            if (!hasBasicAuth && token != null && !token.isEmpty()) {
                // Token/bearer auth against an OpenAI-compatible endpoint exposed at the Ollama host.
                return new OpenAiChatModel.OpenAiChatModelBuilder().baseUrl(host)
                        .modelName(modelName)
                        .apiKey(token)
                        .temperature(temperature)
                        .seed(seed)
                        .build();
            }
            var ollama = OllamaChatModel.builder().baseUrl(host).modelName(modelName).timeout(Duration.ofMinutes(10)).temperature(temperature).seed(seed);
            if (hasBasicAuth) {
                ollama.customHeaders(Map.of("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))));
            }
            return ollama.build();
        });
    }

    /**
     * Creates an OpenAI chat model instance.
     * Requires the OpenAI API key to be set; the organization ID is optional.
     *
     * @return A configured OpenAI chat model instance
     * @throws IllegalStateException If the {@code OPENAI_API_KEY} environment variable is not set
     */
    private ChatModel createOpenAiChatModel() {
        String openAiOrganizationId = environment.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = environment.getenvNonNull("OPENAI_API_KEY");

        // The organization id is optional; when set it is sent to OpenAI, otherwise it is omitted.
        return new LazyChatModel(() -> new OpenAiChatModel.OpenAiChatModelBuilder().modelName(modelName)
                .organizationId(openAiOrganizationId)
                .apiKey(openAiApiKey)
                .temperature(temperature)
                .seed(seed)
                .build());
    }

    /**
     * Creates a Blablador chat model instance.
     * Requires the Blablador API key to be set.
     *
     * @return A configured Blablador chat model instance
     * @throws IllegalStateException If the {@code BLABLADOR_API_KEY} environment variable is not set
     */
    private ChatModel createBlabladorChatModel() {
        String blabladorApiKey = environment.getenvNonNull("BLABLADOR_API_KEY");

        return new LazyChatModel(() -> new OpenAiChatModel.OpenAiChatModelBuilder().baseUrl("https://api.helmholtz-blablador.fz-juelich.de/v1")
                .modelName(modelName)
                .apiKey(blabladorApiKey)
                .temperature(temperature)
                .seed(seed)
                .build());
    }

    /**
     * Creates a DeepSeek chat model instance.
     * Requires the DeepSeek API key to be set.
     *
     * @return A configured DeepSeek chat model instance
     * @throws IllegalStateException If the {@code DEEPSEEK_API_KEY} environment variable is not set
     */
    private ChatModel createDeepSeekChatModel() {
        String deepseekApiKey = environment.getenvNonNull("DEEPSEEK_API_KEY");

        return new LazyChatModel(() -> new OpenAiChatModel.OpenAiChatModelBuilder().baseUrl("https://api.deepseek.com/v1")
                .modelName(modelName)
                .apiKey(deepseekApiKey)
                .temperature(temperature)
                .seed(seed)
                .build());
    }

    /**
     * Creates an Open WebUI chat model instance.
     * Requires the Open WebUI URL and API key to be set.
     *
     * @return A configured Open WebUI chat model instance
     * @throws IllegalStateException If the {@code OPENWEBUI_URL} or {@code OPENWEBUI_API_KEY} environment variable is
     *                               not set
     */
    private ChatModel createOpenWebUIChatModel() {
        String openwebuiUrl = environment.getenvNonNull("OPENWEBUI_URL");
        String openwebuiApiKey = environment.getenvNonNull("OPENWEBUI_API_KEY");

        return new LazyChatModel(() -> new OpenAiChatModel.OpenAiChatModelBuilder().baseUrl(openwebuiUrl)
                .modelName(modelName)
                .apiKey(openwebuiApiKey)
                .temperature(temperature)
                .seed(seed)
                .timeout(Duration.ofMinutes(10))
                .build());
    }

    /**
     * Returns the parameters used to create the cache key for this model.
     * This method is used to identify the cache uniquely.
     *
     * @return The chat cache parameters for this model configuration
     */
    public ChatCacheParameter cacheParameters() {
        return new ChatCacheParameter(modelName, seed, temperature);
    }
}
