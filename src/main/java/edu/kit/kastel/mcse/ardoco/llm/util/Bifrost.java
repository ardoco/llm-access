/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.util.Map;

/**
 * Shared settings for the Bifrost LLM gateway, which exposes an OpenAI-compatible API. The same environment
 * variables are used for chat and embedding models:
 * <ul>
 * <li>{@code BIFROST_API_KEY}: The Bifrost virtual key (required)</li>
 * <li>{@code BIFROST_URL}: The base URL of the OpenAI-compatible API (optional; {@link #DEFAULT_URL} when unset or
 * empty)</li>
 * </ul>
 * Model names are prefixed with their provider, for example {@code openai/gpt-4o-mini}.
 */
public final class Bifrost {
    /** Name of the environment variable holding the virtual key. */
    public static final String API_KEY_VARIABLE = "BIFROST_API_KEY";
    /** Name of the environment variable holding the base URL. */
    public static final String URL_VARIABLE = "BIFROST_URL";
    /** Base URL used when {@value #URL_VARIABLE} is unset or empty. */
    public static final String DEFAULT_URL = "https://llm.vdl.sdq.kastel.kit.edu/v1";
    /** Header through which Bifrost accepts the virtual key (in addition to the bearer token). */
    public static final String VIRTUAL_KEY_HEADER = "x-bf-vk";

    private Bifrost() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Resolves the base URL from the given environment.
     *
     * @param environment The environment to read {@value #URL_VARIABLE} from
     * @return The configured URL, or {@link #DEFAULT_URL} when it is unset or empty
     */
    public static String baseUrl(EnvironmentProvider environment) {
        String url = environment.getenv(URL_VARIABLE);
        return url == null || url.isBlank() ? DEFAULT_URL : url;
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
}
