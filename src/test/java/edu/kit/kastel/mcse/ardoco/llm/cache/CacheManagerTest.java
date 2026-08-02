/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.Environment;

/**
 * Tests for {@link CacheManager}: singleton lifecycle, cache naming and sanitization, conflict detection,
 * persistence, and constructor validation.
 */
@NullMarked
class CacheManagerTest {

    @TempDir
    private Path tempCacheDir;

    @BeforeAll
    static void init() {
        Environment.overwrite(Path.of("src/test/resources/.env-test"));
    }

    @BeforeEach
    void setup() throws IOException {
        CacheManager.setCacheDir(tempCacheDir.toString());
    }

    @AfterEach
    void teardown() {
        CacheManager.resetDefaultInstance();
    }

    @Test
    @DisplayName("getDefaultInstance throws before a cache directory is set")
    void defaultInstanceRequiresDirectory() {
        CacheManager.resetDefaultInstance();
        assertThrows(IllegalStateException.class, CacheManager::getDefaultInstance);
    }

    @Test
    @DisplayName("getCache rejects null arguments")
    void getCacheRejectsNull() {
        CacheManager manager = CacheManager.getDefaultInstance();
        ChatCacheParameter parameter = new ChatCacheParameter("m", 1, 0.0);
        ChatCacheParameter noParameter = null;
        assertThrows(IllegalArgumentException.class, () -> manager.getCache(null, parameter));
        assertThrows(IllegalArgumentException.class, () -> manager.getCache(this, noParameter));
    }

    @Test
    @DisplayName("getCache returns the same instance for the same origin and parameters")
    void getCacheIsMemoized() {
        CacheManager manager = CacheManager.getDefaultInstance();
        ChatCacheParameter parameter = new ChatCacheParameter("m", 1, 0.0);
        Cache<ChatCacheKey> first = manager.getCache(this, parameter);
        Cache<ChatCacheKey> second = manager.getCache(this, parameter);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getCache rejects reusing a cache name with different parameters")
    void getCacheConflictsOnDifferentParameters() {
        CacheManager manager = CacheManager.getDefaultInstance();
        // Two parameters with the same file identifier that are not equal to each other (identity equality).
        manager.getCache(this, new ConstantParameter());
        assertThrows(IllegalArgumentException.class, () -> manager.getCache(this, new ConstantParameter()));
    }

    @Test
    @DisplayName("colons in cache identifiers are sanitized in the file name")
    void sanitizesColonsInFileName() {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> cache = manager.getCache(this, new ChatCacheParameter("a:b", 1, 0.0));
        cache.put("key", "value");
        manager.flush();

        assertTrue(Files.exists(tempCacheDir.resolve("CacheManagerTest_a__b_1.json")));
    }

    @Test
    @DisplayName("flush persists cache entries to disk")
    void flushPersists() throws IOException {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> cache = manager.getCache(this, new ChatCacheParameter("model", 7, 0.0));
        cache.put("prompt", "answer");
        manager.flush();

        Path file = tempCacheDir.resolve("CacheManagerTest_model_7.json");
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("answer"));
    }

    @Test
    @DisplayName("the multi-argument constructor validates its inputs")
    void constructorValidation() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(tempCacheDir, CacheReplacementStrategy.NONE, List.of()));

        Path notADirectory = tempCacheDir.resolve("not-a-directory.txt");
        Files.writeString(notADirectory, "x");
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(notADirectory, CacheReplacementStrategy.NONE, List.of(CacheType.LOCAL)));
    }

    /** A parameter with a constant identifier and default (identity) equality, used to force a name conflict. */
    private static final class ConstantParameter implements CacheParameter<ChatCacheKey> {
        @Override
        public String parameters() {
            return "constant";
        }

        @Override
        public ChatCacheKey createCacheKey(String content) {
            return new ChatCacheParameter("m", 1, 0.0).createCacheKey(content);
        }
    }
}
