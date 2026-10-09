/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MapEnvironment}: lookup, immutability through a defensive copy, and that {@link #toString()}
 * never reveals values.
 */
@NullMarked
class MapEnvironmentTest {

    @Test
    @DisplayName("getenv returns mapped values and null for unknown keys")
    void lookup() {
        MapEnvironment environment = new MapEnvironment(Map.of("API_KEY", "secret"));
        assertEquals("secret", environment.getenv("API_KEY"));
        assertNull(environment.getenv("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("unknown keys do not fall back to the system environment")
    void noSystemFallback() {
        MapEnvironment environment = new MapEnvironment(Map.of());
        assertNull(environment.getenv("PATH"));
    }

    @Test
    @DisplayName("getenvNonNull returns present values and throws for missing ones")
    void nonNull() {
        MapEnvironment environment = new MapEnvironment(Map.of("API_KEY", "secret"));
        assertEquals("secret", environment.getenvNonNull("API_KEY"));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> environment.getenvNonNull("OTHER_KEY"));
        assertTrue(exception.getMessage().contains("OTHER_KEY"));
        assertFalse(exception.getMessage().contains(".env"), "a map environment has nothing to do with .env files");
    }

    @Test
    @DisplayName("later changes to the source map do not affect the environment")
    void defensiveCopy() {
        Map<String, String> source = new HashMap<>();
        source.put("API_KEY", "original");
        MapEnvironment environment = new MapEnvironment(source);

        source.put("API_KEY", "changed");
        source.put("NEW_KEY", "new");

        assertEquals("original", environment.getenv("API_KEY"));
        assertNull(environment.getenv("NEW_KEY"));
    }

    @Test
    @DisplayName("toString lists the keys but never the values")
    void toStringHidesValues() {
        MapEnvironment environment = new MapEnvironment(Map.of("OPENAI_API_KEY", "sk-very-secret", "OLLAMA_HOST", "http://internal-host"));
        String description = environment.toString();

        assertTrue(description.contains("OPENAI_API_KEY"));
        assertTrue(description.contains("OLLAMA_HOST"));
        assertFalse(description.contains("sk-very-secret"));
        assertFalse(description.contains("http://internal-host"));
    }

    @Test
    @DisplayName("environments with the same entries are equal")
    void equality() {
        assertEquals(new MapEnvironment(Map.of("A", "1")), new MapEnvironment(Map.of("A", "1")));
        assertEquals(new MapEnvironment(Map.of("A", "1")).hashCode(), new MapEnvironment(Map.of("A", "1")).hashCode());
        assertNotEquals(new MapEnvironment(Map.of("A", "1")), new MapEnvironment(Map.of("A", "2")));
    }
}
