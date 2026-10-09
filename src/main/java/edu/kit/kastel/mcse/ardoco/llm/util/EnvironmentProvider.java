/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import org.jspecify.annotations.Nullable;

/**
 * Source of configuration values such as API keys, host URLs, and cache settings.
 * <p>
 * Where the values come from is implementation-specific: {@link SystemEnvironment} reads the process environment
 * and an optional {@code .env} file, while {@link MapEnvironment} serves values from an in-memory map.
 */
public interface EnvironmentProvider {
    /**
     * Retrieves an environment variable value. The lookup source is implementation-specific.
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable, or null if not found
     */
    @Nullable
    String getenv(String key);

    /**
     * Retrieves an environment variable value, requiring it to be non-null.
     * This method:
     * <ol>
     * <li>Attempts to retrieve the variable using {@link #getenv(String)}</li>
     * <li>Throws an IllegalStateException if the variable is not set</li>
     * </ol>
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable
     * @throws IllegalStateException if the variable is not set
     */
    default String getenvNonNull(String key) {
        String value = getenv(key);
        if (value == null) {
            throw new IllegalStateException("environment variable %s is missing, use '.env' or your system to set it up".formatted(key));
        }
        return value;
    }
}
