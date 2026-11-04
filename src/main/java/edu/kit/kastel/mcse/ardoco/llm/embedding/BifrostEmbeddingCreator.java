/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.time.Duration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.util.Bifrost;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;

/**
 * An embedding creator that uses the Bifrost LLM gateway (OpenAI-compatible API) for generating embeddings.
 * <p>
 * Environment variables:
 * <ul>
 * <li>{@code BIFROST_API_KEY}: Your Bifrost virtual key (required)</li>
 * <li>{@code BIFROST_URL}: The base URL of the Bifrost API (optional; defaults to
 * {@code https://llm.vdl.sdq.kastel.kit.edu/v1})</li>
 * </ul>
 *
 * Model names are prefixed with their provider, for example {@code openai/text-embedding-3-small}. The creator
 * uses 40 threads for parallel processing of embedding requests, like the OpenAI creator.
 */
public class BifrostEmbeddingCreator extends CachedEmbeddingCreator {
    /** Default number of threads for parallel processing */
    private static final int THREADS = 40;

    /**
     * Creates a new Bifrost embedding creator for the given model.
     *
     * @param model        The provider-prefixed name of the embedding model to use
     * @param environment  The environment that supplies the virtual key and URL
     * @param cacheManager The cache manager that provides the embedding cache
     */
    public BifrostEmbeddingCreator(String model, EnvironmentProvider environment, CacheManager cacheManager) {
        super(model, THREADS, environment, cacheManager);
    }

    /**
     * Creates a Bifrost embedding model instance. The virtual key is sent as bearer token and as {@code x-bf-vk}
     * header.
     *
     * @param model  The provider-prefixed name of the model to use
     * @param params Additional parameters (not used in this implementation)
     * @return A configured Bifrost embedding model instance
     * @throws IllegalStateException If the {@code BIFROST_API_KEY} environment variable is not set
     */
    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String apiKey = Bifrost.apiKey(environment());
        return new OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder().baseUrl(Bifrost.baseUrl(environment()))
                .apiKey(apiKey)
                .customHeaders(Bifrost.headers(apiKey))
                .modelName(model)
                .timeout(Duration.ofMinutes(5))
                .build();
    }
}
