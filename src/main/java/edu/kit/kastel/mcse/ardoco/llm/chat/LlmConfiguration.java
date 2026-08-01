/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Framework-neutral configuration for a chat language model.
 * <p>
 * This record replaces the previously required coupling to any application-specific configuration object.
 * It captures the platform, model name, seed, and temperature that fully determine which chat model is
 * created (and how it is cached). Use {@link #of(ChatModelPlatform)} for defaults or {@link #builder(ChatModelPlatform)}
 * to override individual settings.
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
     * Creates a configuration for the given platform using its default model, seed, and temperature.
     *
     * @param platform The platform that hosts the model
     * @return A configuration with default settings for the platform
     */
    public static LlmConfiguration of(ChatModelPlatform platform) {
        return builder(platform).build();
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
     * Builder for {@link LlmConfiguration} that fills in platform-specific defaults for any value not set.
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
         * Sets the model name. If not set, the platform default is used.
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
         * Builds the configuration, applying the platform default model if none was set.
         *
         * @return The configuration
         */
        public LlmConfiguration build() {
            String resolvedModel = (modelName == null || modelName.isBlank()) ? platform.getDefaultModel() : modelName;
            return new LlmConfiguration(platform, resolvedModel, seed, temperature);
        }
    }
}
