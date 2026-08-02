/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

/**
 * Enum representing supported embedding model platforms. The model name is not part of the platform; it must
 * be provided through the configuration. The number of threads used for parallel generation is decided by
 * the concrete {@link EmbeddingCreator} implementations.
 *
 * <ul>
 * <li>OLLAMA: Local embedding generation using Ollama models</li>
 * <li>OPENAI: Cloud-based embedding generation using OpenAI's API</li>
 * <li>ONNX: Local embedding generation using ONNX models (requires model and tokenizer files)</li>
 * <li>OPENWEBUI: Embedding generation via an Open WebUI server</li>
 * <li>MOCK: Testing implementation returning zero vectors</li>
 * </ul>
 *
 * @see EmbeddingCreator
 */
public enum EmbeddingPlatform {
    /**
     * Ollama platform.
     */
    OLLAMA,
    /**
     * OpenAI platform.
     */
    OPENAI,
    /**
     * ONNX platform. Requires a model name and file paths.
     */
    ONNX,
    /**
     * Open WebUI platform.
     */
    OPENWEBUI,
    /**
     * Mock platform, returns zero vectors.
     */
    MOCK;

    /**
     * Returns the enum value for the given platform name (case-insensitive).
     *
     * @param name the platform name
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the name does not match any platform
     */
    public static EmbeddingPlatform fromString(String name) {
        for (EmbeddingPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(name)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("Unknown embedding platform: " + name);
    }
}
