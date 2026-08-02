/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.chat;

/**
 * Enum representing supported chat language model platforms. The model name is not part of the platform;
 * it must be provided through the configuration.
 *
 * <ul>
 * <li>OPENAI: OpenAI platform</li>
 * <li>OLLAMA: Ollama platform</li>
 * <li>BLABLADOR: Blablador platform</li>
 * <li>DEEPSEEK: DeepSeek platform</li>
 * <li>OPENWEBUI: Open WebUI platform</li>
 * </ul>
 *
 * @see ChatModelProvider
 */
public enum ChatModelPlatform {
    /**
     * OpenAI platform.
     */
    OPENAI,
    /**
     * Ollama platform.
     */
    OLLAMA,
    /**
     * Blablador platform.
     */
    BLABLADOR,
    /**
     * DeepSeek platform.
     */
    DEEPSEEK,
    /**
     * Open WebUI platform.
     */
    OPENWEBUI;

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
