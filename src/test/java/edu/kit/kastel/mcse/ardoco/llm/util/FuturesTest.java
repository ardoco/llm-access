/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link Futures#getLogged}.
 */
@NullMarked
class FuturesTest {

    @Test
    @DisplayName("getLogged returns the resolved value of a completed future")
    void returnsValue() {
        assertEquals("done", Futures.getLogged(CompletableFuture.completedFuture("done"), LoggerFactory.getLogger(FuturesTest.class)));
    }

    @Test
    @DisplayName("getLogged wraps a failed future in an IllegalStateException")
    void wrapsFailure() {
        CompletableFuture<String> failed = CompletableFuture.failedFuture(new RuntimeException("boom"));
        assertThrows(IllegalStateException.class, () -> Futures.getLogged(failed, LoggerFactory.getLogger(FuturesTest.class)));
    }
}
