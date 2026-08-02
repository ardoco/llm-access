/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Framework-neutral configuration for a chat language model.
 * <p>
 * This record replaces the previously required coupling to any application-specific configuration object.
 * It captures the platform, model name, seed, and temperature that fully determine which chat model is
 * created (and how it is cached). Use {@link #of(ChatModelPlatform, String)} for the common case or
 * {@link #builder(ChatModelPlatform)} to override the seed and temperature as well.
 *
 * @param platform    The platform that hosts the model
 * @param modelName   The name of the model to use
 * @param seed        The seed value for model randomization
 * @param temperature The temperature setting for the model
 */
public record LlmConfiguration(ChatModelPlatform platform, String modelName, int seed, double temperature) {

    /**
     * Default seed value for models.
     */
    public static final int DEFAULT_SEED = 133742243;

    /**
     * Default temperature setting for models.
     */
    public static final double DEFAULT_TEMPERATURE = 0.0;

    /**
     * Canonical constructor validating the required arguments.
     *
     * @param platform    The platform that hosts the model
     * @param modelName   The name of the model to use
     * @param seed        The seed value for model randomization
     * @param temperature The temperature setting for the model
     */
    public LlmConfiguration {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }

    /**
     * Creates a configuration for the given platform and model, using the default seed and temperature.
     *
     * @param platform  The platform that hosts the model
     * @param modelName The name of the model to use
     * @return A configuration with the given platform and model and default seed and temperature
     */
    public static LlmConfiguration of(ChatModelPlatform platform, String modelName) {
        return builder(platform).modelName(modelName).build();
    }

    /**
     * Creates a new builder for the given platform.
     *
     * @param platform The platform that hosts the model
     * @return A new builder
     */
    public static Builder builder(ChatModelPlatform platform) {
        return new Builder(platform);
    }

    /**
     * Builder for {@link LlmConfiguration}. The model name is required; the seed and temperature default to
     * {@link #DEFAULT_SEED} and {@link #DEFAULT_TEMPERATURE}.
     */
    public static final class Builder {
        private final ChatModelPlatform platform;
        private @Nullable String modelName;
        private int seed = DEFAULT_SEED;
        private double temperature = DEFAULT_TEMPERATURE;

        private Builder(ChatModelPlatform platform) {
            this.platform = Objects.requireNonNull(platform, "platform must not be null");
        }

        /**
         * Sets the model name. This value is required.
         *
         * @param modelName The name of the model to use
         * @return This builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the seed value.
         *
         * @param seed The seed value for model randomization
         * @return This builder
         */
        public Builder seed(int seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Sets the temperature setting.
         *
         * @param temperature The temperature setting for the model
         * @return This builder
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return The configuration
         * @throws IllegalArgumentException if no model name was set
         */
        public LlmConfiguration build() {
            if (modelName == null || modelName.isBlank()) {
                throw new IllegalArgumentException("A model name must be set for platform " + platform);
            }
            return new LlmConfiguration(platform, modelName, seed, temperature);
        }
    }
}
