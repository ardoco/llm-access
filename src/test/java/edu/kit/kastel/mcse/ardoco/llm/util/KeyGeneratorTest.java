/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KeyGenerator}. The generated keys are part of the on-disk cache format, so they must be
 * deterministic and stable across releases.
 */
@NullMarked
class KeyGeneratorTest {

    @Test
    @DisplayName("generateKey is deterministic for identical input")
    void deterministic() {
        assertEquals(KeyGenerator.generateKey("hello world"), KeyGenerator.generateKey("hello world"));
    }

    @Test
    @DisplayName("generateKey normalizes CRLF to LF")
    void normalizesLineEndings() {
        assertEquals(KeyGenerator.generateKey("line1\nline2"), KeyGenerator.generateKey("line1\r\nline2"));
    }

    @Test
    @DisplayName("different inputs yield different keys")
    void distinctInputs() {
        assertNotEquals(KeyGenerator.generateKey("a"), KeyGenerator.generateKey("b"));
    }

    @Test
    @DisplayName("keys are stable name-based UUIDs (backward compatibility)")
    void stableUuidValue() {
        // Deterministic MD5 name UUID; must remain stable so existing cache files keep resolving.
        assertEquals("098f6bcd-4621-3373-8ade-4e832627b4f6", KeyGenerator.generateKey("test"));
    }

    @Test
    @DisplayName("null input is rejected")
    void nullInput() {
        assertThrows(IllegalArgumentException.class, () -> KeyGenerator.generateKey(null));
    }
}
