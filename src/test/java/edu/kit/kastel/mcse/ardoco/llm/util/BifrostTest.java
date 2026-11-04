/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the environment handling of {@link Bifrost}.
 */
@NullMarked
class BifrostTest {

    @Test
    @DisplayName("the default URL is used when BIFROST_URL is unset, empty or blank")
    void defaultUrl() {
        assertEquals(Bifrost.DEFAULT_URL, Bifrost.baseUrl(new MapEnvironment(Map.of())));
        assertEquals(Bifrost.DEFAULT_URL, Bifrost.baseUrl(new MapEnvironment(Map.of("BIFROST_URL", ""))));
        assertEquals(Bifrost.DEFAULT_URL, Bifrost.baseUrl(new MapEnvironment(Map.of("BIFROST_URL", "  "))));
    }

    @Test
    @DisplayName("a configured BIFROST_URL overrides the default")
    void customUrl() {
        assertEquals("http://localhost:8080/v1", Bifrost.baseUrl(new MapEnvironment(Map.of("BIFROST_URL", "http://localhost:8080/v1"))));
    }

    @Test
    @DisplayName("the API key is required")
    void apiKeyRequired() {
        assertThrows(IllegalStateException.class, () -> Bifrost.apiKey(new MapEnvironment(Map.of())));
        assertEquals("k", Bifrost.apiKey(new MapEnvironment(Map.of("BIFROST_API_KEY", "k"))));
    }

    @Test
    @DisplayName("the virtual key is sent as x-bf-vk header")
    void headers() {
        assertEquals(Map.of("x-bf-vk", "k"), Bifrost.headers("k"));
    }
}
