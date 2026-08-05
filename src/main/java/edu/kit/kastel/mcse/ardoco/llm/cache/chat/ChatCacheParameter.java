/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache.chat;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheParameter;

/**
 * Cache parameters for chat operations.
 * This record encapsulates the configuration parameters that define a unique chat cache,
 * including the model name, random seed, and temperature setting.
 *
 * @param modelName   The name of the language model used for the chat request
 * @param seed        The random seed for reproducible results
 * @param temperature The temperature parameter for controlling randomness in model outputs
 */
public record ChatCacheParameter(String modelName, int seed, double temperature) implements CacheParameter<ChatCacheKey> {
    @Override
    public String parameters() {
        // For backward compatibility, omit temperature if it is 0.0
        if (temperature == 0.0) {
            return String.join("_", modelName, String.valueOf(seed));
        } else {
            return String.join("_", modelName, String.valueOf(seed), String.valueOf(temperature));
        }
    }

    @Override
    public ChatCacheKey createCacheKey(String content) {
        return ChatCacheKey.of(this, content);
    }
}
