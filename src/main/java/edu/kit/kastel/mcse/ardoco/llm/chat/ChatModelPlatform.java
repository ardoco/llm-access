/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

/**
 * Enum representing supported chat language model platforms.
 * Each platform specifies a default number of threads to use for parallel execution and a default model.
 *
 * <ul>
 * <li>OPENAI: OpenAI platform (100 threads)</li>
 * <li>OLLAMA: Ollama platform (1 thread)</li>
 * <li>BLABLADOR: Blablador platform (100 threads)</li>
 * <li>DEEPSEEK: DeepSeek platform (1 thread)</li>
 * <li>OPENWEBUI: Open WebUI platform (10 threads)</li>
 * </ul>
 *
 * @see ChatModelProvider
 */
public enum ChatModelPlatform {
    /**
     * OpenAI platform (100 threads).
     */
    OPENAI(100, "gpt-4o-mini"),
    /**
     * Ollama platform (1 thread).
     */
    OLLAMA(1, "llama3:8b"),
    /**
     * Blablador platform (100 threads).
     */
    BLABLADOR(100, "2 - Llama 3.3 70B instruct"),
    /**
     * DeepSeek platform (1 thread).
     */
    DEEPSEEK(1, "deepseek-chat"),
    /**
     * Open WebUI platform (10 threads).
     */
    OPENWEBUI(10, "llama3:8b");

    private final int threads;
    private final String defaultModel;

    ChatModelPlatform(int threads, String defaultModel) {
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
     * Returns the default model name for this platform.
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
    public static ChatModelPlatform fromString(String name) {
        for (ChatModelPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(name)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("Unknown platform: " + name);
    }
}
