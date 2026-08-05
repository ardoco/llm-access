/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.time.Duration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * An embedding creator that uses Open WebUI for generating embeddings.
 * <p>
 * Required environment variables:
 * <ul>
 * <li>{@code OPENWEBUI_URL}: The URL of the Open WebUI server</li>
 * <li>{@code OPENWEBUI_API_KEY}: API key for authentication</li>
 * </ul>
 *
 * The default model is "nomic-embed-text:v1.5".
 */
public class OpenWebUiEmbeddingCreator extends CachedEmbeddingCreator {

    /**
     * Creates a new Open WebUI embedding creator for the given model.
     *
     * @param model The name of the Open WebUI embedding model to use
     */
    public OpenWebUiEmbeddingCreator(String model) {
        super(model, 1);
    }

    /**
     * Creates an Open WebUI embedding model instance with the specified parameters.
     *
     * @param model  The name of the Open WebUI model to use
     * @param params Additional parameters (not used in this implementation)
     * @return A configured Open WebUI embedding model instance
     */
    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String url = Environment.getenvNonNull("OPENWEBUI_URL");
        String apiKey = Environment.getenvNonNull("OPENWEBUI_API_KEY");

        var openWebUiEmbeddingModel = new OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder().baseUrl(url)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofMinutes(5));
        return openWebUiEmbeddingModel.build();
    }
}
