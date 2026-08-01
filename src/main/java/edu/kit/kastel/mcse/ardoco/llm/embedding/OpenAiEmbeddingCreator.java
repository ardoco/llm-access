/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * An embedding creator that uses OpenAI's embedding models for generating embeddings.
 * <p>
 * Required environment variables:
 * <ul>
 * <li>{@code OPENAI_ORGANIZATION_ID}: Your OpenAI organization ID</li>
 * <li>{@code OPENAI_API_KEY}: Your OpenAI API key</li>
 * </ul>
 *
 * The default model is "text-embedding-ada-002". The creator uses 40 threads by default for parallel
 * processing of embedding requests.
 */
public class OpenAiEmbeddingCreator extends CachedEmbeddingCreator {
    /** Default number of threads for parallel processing */
    private static final int THREADS = 40;

    /**
     * Creates a new OpenAI embedding creator for the given model.
     *
     * @param model The name of the OpenAI embedding model to use
     */
    public OpenAiEmbeddingCreator(String model) {
        super(model, THREADS);
    }

    /**
     * Creates an OpenAI embedding model instance with the specified parameters.
     * The method requires both the organization ID and API key to be set in the environment variables.
     *
     * @param model  The name of the OpenAI model to use
     * @param params Additional parameters (not used in this implementation)
     * @return A configured OpenAI embedding model instance
     * @throws IllegalStateException If either OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable is not set
     */
    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String openAiOrganizationId = Environment.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = Environment.getenv("OPENAI_API_KEY");
        if (openAiOrganizationId == null || openAiApiKey == null) {
            throw new IllegalStateException("OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable not set");
        }
        return new OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder().modelName(model)
                .organizationId(openAiOrganizationId)
                .apiKey(openAiApiKey)
                .maxRetries(0)
                .build();
    }
}
