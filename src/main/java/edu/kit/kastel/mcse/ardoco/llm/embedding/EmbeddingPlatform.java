/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

/**
 * Enum representing supported embedding model platforms.
 * Each platform specifies a default number of threads for parallel embedding generation and a default model.
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
     * Ollama platform (1 thread).
     */
    OLLAMA(1, "nomic-embed-text:v1.5"),
    /**
     * OpenAI platform (40 threads).
     */
    OPENAI(40, "text-embedding-ada-002"),
    /**
     * ONNX platform (1 thread). Has no default model; a model name and file paths must be provided.
     */
    ONNX(1, ""),
    /**
     * Open WebUI platform (1 thread).
     */
    OPENWEBUI(1, "nomic-embed-text:v1.5"),
    /**
     * Mock platform (1 thread), returns zero vectors.
     */
    MOCK(1, "mock");

    private final int threads;
    private final String defaultModel;

    EmbeddingPlatform(int threads, String defaultModel) {
        this.threads = threads;
        this.defaultModel = defaultModel;
    }

    /**
     * Returns the number of threads for this platform.
     *
     * @return the thread count
     */
    public int getThreads() {
        return threads;
    }

    /**
     * Returns the default model name for this platform (empty if none).
     *
     * @return the default model name
     */
    public String getDefaultModel() {
        return defaultModel;
    }

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
