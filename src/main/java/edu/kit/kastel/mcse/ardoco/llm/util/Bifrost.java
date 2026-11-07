/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.util.Map;

/**
 * Shared settings for the Bifrost LLM gateway, which exposes an OpenAI-compatible API. The same environment
 * variables are used for chat and embedding models:
 * <ul>
 * <li>{@code BIFROST_API_KEY}: The Bifrost virtual key (required)</li>
 * <li>{@code BIFROST_URL}: The base URL of the OpenAI-compatible API (required)</li>
 * </ul>
 * Model names are prefixed with their provider, for example {@code openai/gpt-4o-mini}.
 */
public final class Bifrost {
    /** Name of the environment variable holding the virtual key. */
    public static final String API_KEY_VARIABLE = "BIFROST_API_KEY";
    /** Name of the environment variable holding the base URL. */
    public static final String URL_VARIABLE = "BIFROST_URL";
    /** Header through which Bifrost accepts the virtual key (in addition to the bearer token). */
    public static final String VIRTUAL_KEY_HEADER = "x-bf-vk";

    private Bifrost() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Resolves the base URL from the given environment.
     *
     * @param environment The environment to read {@value #URL_VARIABLE} from
     * @return The configured URL
     * @throws IllegalStateException If the variable is not set
     */
    public static String baseUrl(EnvironmentProvider environment) {
        return environment.getenvNonNull(URL_VARIABLE);
    }

    /**
     * Reads the virtual key from the given environment.
     *
     * @param environment The environment to read {@value #API_KEY_VARIABLE} from
     * @return The virtual key
     * @throws IllegalStateException If the variable is not set
     */
    public static String apiKey(EnvironmentProvider environment) {
        return environment.getenvNonNull(API_KEY_VARIABLE);
    }

    /**
     * Creates the custom headers that carry the virtual key.
     *
     * @param apiKey The virtual key
     * @return The headers to send with every request
     */
    public static Map<String, String> headers(String apiKey) {
        return Map.of(VIRTUAL_KEY_HEADER, apiKey);
    }

    /**
     * Derives the model name used in cache identities from a Bifrost model name. The provider prefix (everything up to
     * and including the first {@code /}) is removed so that Bifrost shares cache entries with the native platforms.
     * Requests to the gateway keep the prefixed name.
     *
     * @param modelName The provider-prefixed model name, e.g., {@code openai/gpt-4o-mini}
     * @return The model name without provider prefix, e.g., {@code gpt-4o-mini}; unchanged if it has no {@code /}
     * @throws IllegalArgumentException If nothing remains after removing the prefix
     */
    public static String cacheModelName(String modelName) {
        String name = modelName.substring(modelName.indexOf('/') + 1);
        if (name.isBlank()) {
            throw new IllegalArgumentException("Bifrost model name has no model after the provider prefix: " + modelName);
        }
        return name;
    }
}
