/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import java.util.concurrent.Future;

import org.slf4j.Logger;

/**
 * Utility methods for working with {@link Future} instances.
 */
public final class Futures {
    private Futures() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Retrieves the result of a future, logging and rethrowing any error as an unchecked exception.
     *
     * @param <T>    The result type of the future
     * @param future The future to resolve
     * @param logger The logger used to report failures
     * @return The resolved value of the future
     * @throws IllegalStateException If the future was interrupted or failed
     */
    @SuppressWarnings("java:S2139")
    public static <T> T getLogged(Future<T> future, Logger logger) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for future", e);
            Thread.currentThread().interrupt(); // Restore the interrupted status
            throw new IllegalStateException("Thread was interrupted while waiting for future result", e);
        } catch (Exception e) {
            logger.error("Error while getting future result: {}", e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }
}
