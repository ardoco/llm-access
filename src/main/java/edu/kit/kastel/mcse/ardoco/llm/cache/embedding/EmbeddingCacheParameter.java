/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache.embedding;

import java.util.Objects;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.cache.LargeLanguageModelCacheMode;

/**
 * Cache parameters for embedding operations.
 * This record encapsulates the configuration parameters that define a unique embedding cache.
 * For embeddings, only the model name is required as embeddings are deterministic.
 *
 * @param modelName The name of the embedding model used for generating embeddings
 */
public record EmbeddingCacheParameter(String modelName) implements CacheParameter<EmbeddingCacheKey> {
    /**
     * Creates the cache parameters.
     *
     * @param modelName The name of the embedding model used for generating embeddings
     */
    public EmbeddingCacheParameter {
        Objects.requireNonNull(modelName, "modelName must not be null");
    }

    @Override
    public String parameters() {
        return modelName;
    }

    @Override
    public LargeLanguageModelCacheMode mode() {
        return LargeLanguageModelCacheMode.EMBEDDING;
    }

    @Override
    public EmbeddingCacheKey createCacheKey(String content) {
        return EmbeddingCacheKey.of(this, content);
    }
}
