/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import org.jspecify.annotations.Nullable;

public interface EnvironmentProvider {
    /**
     * Retrieves an environment variable value.
     * This method:
     * <ol>
     * <li>First checks the .env file for the variable</li>
     * <li>If not found, falls back to system environment variables</li>
     * <li>Returns null if the variable is not found in either location</li>
     * </ol>
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
     * <li>Throws an IllegalStateException if environment variable would be null</li>
     * </ol>
     *
     * @param key The name of the environment variable to retrieve
     * @return The value of the environment variable
     * @throws IllegalStateException if the variable is not found and strict mode is enabled
     */
    String getenvNonNull(String key);
}
