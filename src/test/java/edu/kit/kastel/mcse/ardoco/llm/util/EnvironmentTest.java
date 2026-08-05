/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Environment}: {@code .env} loading, the fallback to system environment variables, and
 * {@link Environment#overwrite}. The environment is process-global, so the shared test {@code .env} is
 * restored after each test.
 */
@NullMarked
class EnvironmentTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void restore() {
        Environment.overwrite(Path.of("src/test/resources/.env-test"));
    }

    private Path writeEnv(String content) throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, content);
        Environment.overwrite(env);
        return env;
    }

    @Test
    @DisplayName("values are read from the loaded .env file")
    void readsFromDotEnv() throws IOException {
        writeEnv("MY_TEST_KEY=my-value\n");
        assertEquals("my-value", Environment.getenv("MY_TEST_KEY"));
    }

    @Test
    @DisplayName("getenv falls back to the system environment for keys absent from .env")
    void fallsBackToSystemEnv() throws IOException {
        writeEnv("MY_TEST_KEY=my-value\n");
        // PATH is not defined in the .env, so getenv must yield the system value (null or otherwise).
        assertEquals(System.getenv("PATH"), Environment.getenv("PATH"));
    }

    @Test
    @DisplayName("getenv returns null for a completely unknown variable")
    void unknownReturnsNull() throws IOException {
        writeEnv("MY_TEST_KEY=my-value\n");
        assertNull(Environment.getenv("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("getenvNonNull throws for a missing variable")
    void nonNullThrows() throws IOException {
        writeEnv("MY_TEST_KEY=my-value\n");
        assertThrows(IllegalStateException.class, () -> Environment.getenvNonNull("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("overwrite with a non-existent path keeps the previous configuration")
    void overwriteMissingKeepsPrevious() throws IOException {
        writeEnv("MY_TEST_KEY=keep-me\n");
        Environment.overwrite(tempDir.resolve("does-not-exist.env"));
        assertEquals("keep-me", Environment.getenv("MY_TEST_KEY"));
    }
}
