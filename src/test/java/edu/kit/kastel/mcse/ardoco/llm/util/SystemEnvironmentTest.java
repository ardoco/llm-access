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

import io.github.cdimascio.dotenv.DotenvException;

/**
 * Tests for {@link SystemEnvironment}: loading an explicit {@code .env} file, the fallback to system environment
 * variables, lazy loading, and the independence of instances.
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
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> environment.getenvNonNull("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
        assertTrue(exception.getMessage().contains(tempDir.resolve(".env").toString()), "the message should point to the .env file");
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

    @Test
    @DisplayName("the .env file is read on the first lookup, not on construction, and then kept")
    void loadsLazilyOnce() throws IOException {
        Path env = tempDir.resolve("lazy.env");
        SystemEnvironment environment = new SystemEnvironment(env);

        Files.writeString(env, "MY_TEST_KEY=first\n");
        assertEquals("first", environment.getenv("MY_TEST_KEY"), "the file must be read on the first lookup");

        Files.writeString(env, "MY_TEST_KEY=second\n");
        assertEquals("first", environment.getenv("MY_TEST_KEY"), "the loaded file must not be re-read");
    }

    @Test
    @DisplayName("a malformed .env file does not fail construction, only the lookups that need it")
    void malformedFileFailsOnLookupOnly() throws IOException {
        Path env = tempDir.resolve("malformed.env");
        Files.writeString(env, "this line is not a key-value pair\n");

        SystemEnvironment environment = assertDoesNotThrow(() -> new SystemEnvironment(env));
        assertThrows(DotenvException.class, () -> environment.getenv("LLM_ACCESS_DEFINITELY_UNSET_VARIABLE"));
    }

    @Test
    @DisplayName("a .env file inside a directory whose name ends with .env is loaded")
    void loadsFromDirectoryEndingWithDotEnv() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("conf.env"));
        Path env = directory.resolve(".env");
        Files.writeString(env, "MY_TEST_KEY=from-conf\n");

        assertEquals("from-conf", new SystemEnvironment(env).getenv("MY_TEST_KEY"));
    }

    @Test
    @DisplayName("instances on the same file are unequal if they loaded different entries")
    void equalityComparesLoadedEntries() throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "MY_TEST_KEY=before\n");
        SystemEnvironment before = new SystemEnvironment(env);
        assertEquals("before", before.getenv("MY_TEST_KEY"));

        Files.writeString(env, "MY_TEST_KEY=after\n");
        SystemEnvironment after = new SystemEnvironment(env);

        assertNotEquals(before, after);
        assertEquals(after, new SystemEnvironment(env));
        assertEquals(after.hashCode(), new SystemEnvironment(env).hashCode());
    }
}
