/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;

/**
 * An embedding creator that uses OpenAI's embedding models for generating embeddings.
 * <p>
 * Environment variables:
 * <ul>
 * <li>{@code OPENAI_API_KEY}: Your OpenAI API key</li>
 * <li>{@code OPENAI_ORGANIZATION_ID}: (Optional) Your OpenAI organization ID, sent only when set</li>
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
     * @param model        The name of the OpenAI embedding model to use
     * @param environment  The environment that supplies credentials and host URLs
     * @param cacheManager The cache manager that provides the embedding cache
     */
    public OpenAiEmbeddingCreator(String model, EnvironmentProvider environment, CacheManager cacheManager) {
        super(model, THREADS, environment, cacheManager);
    }

    /**
     * Creates an OpenAI embedding model instance with the specified parameters.
     * The method requires the API key to be set in the environment; the organization ID is optional.
     *
     * @param model  The name of the OpenAI model to use
     * @param params Additional parameters (not used in this implementation)
     * @return A configured OpenAI embedding model instance
     * @throws IllegalStateException If the OPENAI_API_KEY environment variable is not set
     */
    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String openAiOrganizationId = environment().getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = environment().getenvNonNull("OPENAI_API_KEY");
        // The organization id is optional; when set it is sent to OpenAI, otherwise it is omitted.
        return new OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder().modelName(model)
                .organizationId(openAiOrganizationId)
                .apiKey(openAiApiKey)
                .maxRetries(0)
                .build();
    }
}
