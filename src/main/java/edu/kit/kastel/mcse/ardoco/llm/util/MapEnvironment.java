/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that simulates environment variables using a {@code Map<String, String>}.
 * This can be used as a drop-in replacement for the {@link SystemEnvironment} class when
 * environment variables should be provided programmatically rather than from a .env file.
 */
public record MapEnvironment(Map<String, String> envMap) implements EnvironmentProvider {
    /**
     * Creates a new MapEnvironment instance initialized with the provided map.
     *
     * @param envMap A map containing environment variable names as keys and their values as values.
     */
    public MapEnvironment(Map<String, String> envMap) {
        this.envMap = new HashMap<>(envMap);
    }

    @Override
    public String getenv(String name) {
        return envMap.get(name);
    }

}
