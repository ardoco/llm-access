/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;
import edu.kit.kastel.mcse.ardoco.llm.util.SystemEnvironment;

/**
 * Framework-neutral configuration for an embedding model.
 * <p>
 * Captures the platform, model name, (for the ONNX platform) the local model and tokenizer file paths, and the
 * environment that supplies credentials and host URLs.
 * Use {@link #of(EmbeddingPlatform, String)} for the common case, {@link #onnx(String, String, String)} for
 * ONNX models, or {@link #builder(EmbeddingPlatform)} to set the file paths as well.
 *
 * @param platform        The embedding platform
 * @param modelName       The name of the embedding model
 * @param pathToModel     The path to the ONNX model file (only used by the ONNX platform)
 * @param pathToTokenizer The path to the ONNX tokenizer file (only used by the ONNX platform)
 * @param environment     The environment that supplies credentials and host URLs
 */
public record EmbeddingConfiguration(EmbeddingPlatform platform, String modelName, @Nullable String pathToModel, @Nullable String pathToTokenizer,
                                     EnvironmentProvider environment) {

    /**
     * Canonical constructor validating the required arguments.
     *
     * @param platform        The embedding platform
     * @param modelName       The name of the embedding model
     * @param pathToModel     The path to the ONNX model file (only used by the ONNX platform)
     * @param pathToTokenizer The path to the ONNX tokenizer file (only used by the ONNX platform)
     * @param environment     The environment that supplies credentials and host URLs
     */
    public EmbeddingConfiguration {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
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
     * {@link EmbeddingPlatform#MOCK}, which ignores it. The environment defaults to a new {@link SystemEnvironment}.
     */
    public static final class Builder {
        private final EmbeddingPlatform platform;
        private @Nullable String modelName;
        private @Nullable String pathToModel;
        private @Nullable String pathToTokenizer;
        private @Nullable EnvironmentProvider environment;

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
         * Sets the environment that supplies credentials and host URLs, for example a {@link MapEnvironment}.
         * If no environment is set, {@link #build()} creates a new {@link SystemEnvironment}.
         *
         * @param environment The environment provider to read credentials and host URLs from
         * @return This builder
         */
        public Builder environment(EnvironmentProvider environment) {
            this.environment = Objects.requireNonNull(environment, "environment must not be null");
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
            EnvironmentProvider resolvedEnvironment = environment != null ? environment : new SystemEnvironment();
            return new EmbeddingConfiguration(platform, resolvedModel, pathToModel, pathToTokenizer, resolvedEnvironment);
        }
    }
}
