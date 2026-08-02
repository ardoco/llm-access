/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Framework-neutral configuration for an embedding model.
 * <p>
 * Captures the platform, model name, and (for the ONNX platform) the local model and tokenizer file paths.
 * Use {@link #of(EmbeddingPlatform, String)} for the common case, {@link #onnx(String, String, String)} for
 * ONNX models, or {@link #builder(EmbeddingPlatform)} to set the file paths as well.
 *
 * @param platform        The embedding platform
 * @param modelName       The name of the embedding model
 * @param pathToModel     The path to the ONNX model file (only used by the ONNX platform)
 * @param pathToTokenizer The path to the ONNX tokenizer file (only used by the ONNX platform)
 */
public record EmbeddingConfiguration(EmbeddingPlatform platform, String modelName, @Nullable String pathToModel, @Nullable String pathToTokenizer) {

    /**
     * Canonical constructor validating the required arguments.
     *
     * @param platform        The embedding platform
     * @param modelName       The name of the embedding model
     * @param pathToModel     The path to the ONNX model file (only used by the ONNX platform)
     * @param pathToTokenizer The path to the ONNX tokenizer file (only used by the ONNX platform)
     */
    public EmbeddingConfiguration {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }

    /**
     * Creates a configuration for the given platform and model.
     *
     * @param platform  The embedding platform
     * @param modelName The name of the embedding model
     * @return A configuration with the given platform and model
     */
    public static EmbeddingConfiguration of(EmbeddingPlatform platform, String modelName) {
        return builder(platform).modelName(modelName).build();
    }

    /**
     * Creates an ONNX configuration with the given model name and file paths.
     *
     * @param model           The name of the model (used for tokenizer selection and cache identification)
     * @param pathToModel     The path to the ONNX model file
     * @param pathToTokenizer The path to the ONNX tokenizer file
     * @return An ONNX embedding configuration
     */
    public static EmbeddingConfiguration onnx(String model, String pathToModel, String pathToTokenizer) {
        return builder(EmbeddingPlatform.ONNX).modelName(model).pathToModel(pathToModel).pathToTokenizer(pathToTokenizer).build();
    }

    /**
     * Creates a new builder for the given platform.
     *
     * @param platform The embedding platform
     * @return A new builder
     */
    public static Builder builder(EmbeddingPlatform platform) {
        return new Builder(platform);
    }

    /**
     * Builder for {@link EmbeddingConfiguration}. The model name is required for every platform except
     * {@link EmbeddingPlatform#MOCK}, which ignores it.
     */
    public static final class Builder {
        private final EmbeddingPlatform platform;
        private @Nullable String modelName;
        private @Nullable String pathToModel;
        private @Nullable String pathToTokenizer;

        private Builder(EmbeddingPlatform platform) {
            this.platform = Objects.requireNonNull(platform, "platform must not be null");
        }

        /**
         * Sets the model name. This value is required.
         *
         * @param modelName The name of the embedding model
         * @return This builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the path to the ONNX model file.
         *
         * @param pathToModel The path to the ONNX model file
         * @return This builder
         */
        public Builder pathToModel(String pathToModel) {
            this.pathToModel = pathToModel;
            return this;
        }

        /**
         * Sets the path to the ONNX tokenizer file.
         *
         * @param pathToTokenizer The path to the ONNX tokenizer file
         * @return This builder
         */
        public Builder pathToTokenizer(String pathToTokenizer) {
            this.pathToTokenizer = pathToTokenizer;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return The configuration
         * @throws IllegalArgumentException if no model name was set for a non-mock platform
         */
        public EmbeddingConfiguration build() {
            String resolvedModel = modelName;
            if (resolvedModel == null || resolvedModel.isBlank()) {
                // The mock platform ignores the model entirely, so a name is optional there;
                // every real platform requires one.
                if (platform != EmbeddingPlatform.MOCK) {
                    throw new IllegalArgumentException("A model name must be set for platform " + platform);
                }
                resolvedModel = "mock";
            }
            return new EmbeddingConfiguration(platform, resolvedModel, pathToModel, pathToTokenizer);
        }
    }
}
