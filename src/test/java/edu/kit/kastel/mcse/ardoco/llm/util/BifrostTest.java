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
    @DisplayName("the configured BIFROST_URL is returned")
    void configuredUrl() {
        assertEquals("http://localhost:8080/v1", Bifrost.baseUrl(new MapEnvironment(Map.of("BIFROST_URL", "http://localhost:8080/v1"))));
    }

    @Test
    @DisplayName("the URL is required")
    void urlRequired() {
        assertThrows(IllegalStateException.class, () -> Bifrost.baseUrl(new MapEnvironment(Map.of())));
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
