/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.util.Map;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * An {@link EnvironmentProvider} backed by an immutable in-memory map.
 * <p>
 * Use it instead of {@link SystemEnvironment} when credentials and hosts should be supplied programmatically
 * rather than read from the process environment or a {@code .env} file. Keys that are absent from the map are
 * reported as unset; there is no fallback to the system environment.
 * <p>
 * The map is copied on construction, so later changes to the caller's map have no effect. {@link #toString()}
 * lists only the keys, never the values, so that secrets do not end up in logs.
 */
public final class MapEnvironment implements EnvironmentProvider {
    private final Map<String, String> values;

    /**
     * Creates a new environment holding a copy of the given variables.
     *
     * @param values A map from environment variable names to their values
     * @throws NullPointerException if the map or any of its keys or values is null
     */
    public MapEnvironment(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    @Override
    public @Nullable String getenv(String key) {
        return values.get(key);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof MapEnvironment other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * Returns a description listing the variable names only. Values are deliberately omitted because they
     * typically contain secrets such as API keys.
     *
     * @return A description of this environment without any values
     */
    @Override
    public String toString() {
        return "MapEnvironment" + new TreeSet<>(values.keySet());
    }
}
