/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SystemEnvironment}: loading an explicit {@code .env} file, the fallback to system environment
 * variables, and the independence of instances.
 */
@NullMarked
class SystemEnvironmentTest {

    @TempDir
    private Path tempDir;

    private SystemEnvironment environmentWith(String fileName, String content) throws IOException {
        Path env = tempDir.resolve(fileName);
        Files.writeString(env, content);
        return new SystemEnvironment(env);
    }

    @Test
    @DisplayName("values are read from the given .env file")
    void readsFromDotEnv() throws IOException {
        SystemEnvironment environment = environmentWith(".env", "MY_TEST_KEY=my-value\n");
        assertEquals("my-value", environment.getenv("MY_TEST_KEY"));
    }

    @Test
    @DisplayName("getenv falls back to the system environment for keys absent from .env")
    void fallsBackToSystemEnv() throws IOException {
        SystemEnvironment environment = environmentWith(".env", "MY_TEST_KEY=my-value\n");
        // PATH is not defined in the .env, so getenv must yield the system value (null or otherwise).
        assertEquals(System.getenv("PATH"), environment.getenv("PATH"));
    }

    @Test
    @DisplayName("getenv returns null for a completely unknown variable")
    void unknownReturnsNull() throws IOException {
        SystemEnvironment environment = environmentWith(".env", "MY_TEST_KEY=my-value\n");
        assertNull(environment.getenv("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("getenvNonNull returns present values and throws for a missing variable")
    void nonNull() throws IOException {
        SystemEnvironment environment = environmentWith(".env", "MY_TEST_KEY=my-value\n");
        assertEquals("my-value", environment.getenvNonNull("MY_TEST_KEY"));
        assertThrows(IllegalStateException.class, () -> environment.getenvNonNull("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("a non-existent .env file falls back to the system environment only")
    void missingFileUsesSystemEnvironment() {
        SystemEnvironment environment = new SystemEnvironment(tempDir.resolve("does-not-exist.env"));
        assertNull(environment.getenv("MY_TEST_KEY"));
        assertEquals(System.getenv("PATH"), environment.getenv("PATH"));
    }

    @Test
    @DisplayName("instances loading different files are independent of each other")
    void instancesAreIndependent() throws IOException {
        SystemEnvironment first = environmentWith("first.env", "MY_TEST_KEY=first\n");
        SystemEnvironment second = environmentWith("second.env", "MY_TEST_KEY=second\n");

        assertEquals("first", first.getenv("MY_TEST_KEY"));
        assertEquals("second", second.getenv("MY_TEST_KEY"));
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("instances loading the same file are equal and do not expose values in toString")
    void equalityAndToString() throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "MY_SECRET_KEY=super-secret\n");

        SystemEnvironment environment = new SystemEnvironment(env);
        assertEquals(environment, new SystemEnvironment(env));
        assertEquals(environment.hashCode(), new SystemEnvironment(env).hashCode());
        assertFalse(environment.toString().contains("super-secret"));
    }
}
