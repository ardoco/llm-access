/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.cache.embedding.EmbeddingCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.cache.embedding.EmbeddingCacheParameter;
import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;
import edu.kit.kastel.mcse.ardoco.llm.util.MapEnvironment;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * Tests for {@link CacheManager}: singleton lifecycle, cache naming and sanitization, conflict detection,
 * persistence, and constructor validation.
 */
@NullMarked
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheManagerTest {
    private final EnvironmentProvider environment = new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR"));

    @TempDir
    private Path tempCacheDir;

    @BeforeEach
    void setup() throws IOException {
        CacheManager.setCacheDir(tempCacheDir.toString(), environment);
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
        ChatCacheParameter noParameter = null;
        assertThrows(IllegalArgumentException.class, () -> manager.getCache(noParameter));
    }

    @Test
    @DisplayName("getCache returns the same instance for equal parameters")
    void getCacheIsMemoized() {
        CacheManager manager = CacheManager.getDefaultInstance();
        ChatCacheParameter parameter = new ChatCacheParameter("m", 1, 0.0);
        Cache<ChatCacheKey> first = manager.getCache(parameter);
        Cache<ChatCacheKey> second = manager.getCache(new ChatCacheParameter("m", 1, 0.0));
        assertSame(first, second);
    }

    @Test
    @DisplayName("getCache rejects reusing a cache name with different parameters")
    void getCacheConflictsOnDifferentParameters() {
        CacheManager manager = CacheManager.getDefaultInstance();
        // Two parameters with the same file identifier that are not equal to each other (identity equality).
        manager.getCache(new ConstantParameter());
        assertThrows(IllegalArgumentException.class, () -> manager.getCache(new ConstantParameter()));
    }

    @Test
    @DisplayName("cache names are Chat_ and Embedding_ plus the parameters, independent of the caller")
    void cacheNamesFollowModeAndParameters() {
        CacheManager manager = CacheManager.getDefaultInstance();
        manager.getCache(new ChatCacheParameter("gpt-4o-mini", 42, 0.0)).put("key", "value");
        manager.getCache(new ChatCacheParameter("gpt-4o-mini", 42, 0.5)).put("key", "value");
        manager.getCache(new EmbeddingCacheParameter("text-embedding-3-small")).put("key", new float[] { 1.0f });
        manager.flush();

        assertTrue(Files.exists(tempCacheDir.resolve("Chat_gpt-4o-mini_42.json")));
        assertTrue(Files.exists(tempCacheDir.resolve("Chat_gpt-4o-mini_42_0.5.json")));
        assertTrue(Files.exists(tempCacheDir.resolve("Embedding_text-embedding-3-small.json")));
    }

    @Test
    @DisplayName("a chat and an embedding parameter with the same parameters() use separate files")
    void chatAndEmbeddingCachesAreSeparate() {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> chat = manager.getCache(new ModeParameter(LargeLanguageModelCacheMode.CHAT, "same"));
        Cache<ChatCacheKey> embedding = manager.getCache(new ModeParameter(LargeLanguageModelCacheMode.EMBEDDING, "same"));
        chat.put("key", "chat-value");
        embedding.put("key", "embedding-value");
        manager.flush();

        assertNotSame(chat, embedding);
        assertTrue(Files.exists(tempCacheDir.resolve("Chat_same.json")));
        assertTrue(Files.exists(tempCacheDir.resolve("Embedding_same.json")));
        assertEquals("chat-value", chat.get("key", String.class));
        assertEquals("embedding-value", embedding.get("key", String.class));
    }

    @Test
    @DisplayName("end to end: a new manager reads migrated-style Chat_ and Embedding_ files")
    void readsMigratedStyleFiles() throws IOException {
        Path fixtures = Path.of("src/test/resources/cache");
        Files.copy(fixtures.resolve("Chat_gpt-4o-mini_42.json"), tempCacheDir.resolve("Chat_gpt-4o-mini_42.json"));
        Files.copy(fixtures.resolve("Embedding_text-embedding-3-small.json"), tempCacheDir.resolve("Embedding_text-embedding-3-small.json"));

        CacheManager fresh = new CacheManager(tempCacheDir, environment);

        assertEquals("world", fresh.getCache(new ChatCacheParameter("gpt-4o-mini", 42, 0.0)).get("hello", String.class));
        assertArrayEquals(new float[] { 0.5f, -1.0f, 2.0f }, fresh.getCache(new EmbeddingCacheParameter("text-embedding-3-small")).get("hello", float[].class));
    }

    @Test
    @DisplayName("colons in cache identifiers are sanitized in the file name")
    void sanitizesColonsInFileName() {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> cache = manager.getCache(new ChatCacheParameter("a:b", 1, 0.0));
        cache.put("key", "value");
        manager.flush();

        assertTrue(Files.exists(tempCacheDir.resolve("Chat_a__b_1.json")));
    }

    @Test
    @DisplayName("slashes in model names are encoded so chat and embedding caches are written directly into the cache directory")
    void encodesPathSeparatorsInFileName() throws IOException {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> chat = manager.getCache(new ChatCacheParameter("openai/gpt-4o-mini", 1, 0.0));
        Cache<EmbeddingCacheKey> embedding = manager.getCache(new EmbeddingCacheParameter("openai/text-embedding-3-small"));
        chat.put("prompt", "answer");
        embedding.put("text", new float[] { 1.0f, 2.0f });
        assertDoesNotThrow(manager::flush);

        assertTrue(Files.exists(tempCacheDir.resolve("Chat_openai--gpt-4o-mini_1.json")));
        assertTrue(Files.exists(tempCacheDir.resolve("Embedding_openai--text-embedding-3-small.json")));
        try (var entries = Files.list(tempCacheDir)) {
            assertTrue(entries.noneMatch(Files::isDirectory), "no subdirectory may be created");
        }

        CacheManager reloaded = new CacheManager(tempCacheDir, environment);
        assertEquals("answer", reloaded.getCache(new ChatCacheParameter("openai/gpt-4o-mini", 1, 0.0)).get("prompt", String.class));
        assertArrayEquals(new float[] { 1.0f, 2.0f }, reloaded.getCache(new EmbeddingCacheParameter("openai/text-embedding-3-small"))
                .get("text", float[].class));
    }

    @Test
    @DisplayName("backslashes in cache identifiers are encoded in the file name")
    void encodesBackslashesInFileName() {
        CacheManager manager = CacheManager.getDefaultInstance();
        manager.getCache(new ChatCacheParameter("a\\b", 1, 0.0)).put("key", "value");
        manager.flush();

        assertTrue(Files.exists(tempCacheDir.resolve("Chat_a--b_1.json")));
    }

    @Test
    @DisplayName("flush persists cache entries to disk")
    void flushPersists() throws IOException {
        CacheManager manager = CacheManager.getDefaultInstance();
        Cache<ChatCacheKey> cache = manager.getCache(new ChatCacheParameter("model", 7, 0.0));
        cache.put("prompt", "answer");
        manager.flush();

        Path file = tempCacheDir.resolve("Chat_model_7.json");
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("answer"));
    }

    @Test
    @DisplayName("the multi-argument constructor validates its inputs")
    void constructorValidation() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(tempCacheDir, CacheReplacementStrategy.NONE, List.of(), environment));

        Path notADirectory = tempCacheDir.resolve("not-a-directory.txt");
        Files.writeString(notADirectory, "x");
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(notADirectory, CacheReplacementStrategy.NONE, List.of(CacheType.LOCAL),
                environment));
    }

    @Test
    @DisplayName("the cache configuration is read from the injected environment")
    void readsConfigurationFromInjectedEnvironment() {
        EnvironmentProvider invalidHierarchy = new MapEnvironment(Map.of("CACHE_HIERARCHY", "NOT_A_CACHE"));
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(tempCacheDir, invalidHierarchy));

        EnvironmentProvider invalidStrategy = new MapEnvironment(Map.of("CACHE_REPLACEMENT_STRATEGY", "NOT_A_STRATEGY"));
        assertThrows(IllegalArgumentException.class, () -> new CacheManager(tempCacheDir, invalidStrategy));

        assertDoesNotThrow(() -> new CacheManager(tempCacheDir, new MapEnvironment(Map.of())), "unset variables fall back to the defaults");
    }

    @Test
    @DisplayName("getCache passes the injected environment on to the Redis cache")
    void passesEnvironmentToRedisCache() throws IOException {
        // Port 1 is never a Redis server: if REDIS_URL reaches the Redis cache, connecting to it must fail.
        EnvironmentProvider unreachableRedis = new MapEnvironment(Map.of("CACHE_HIERARCHY", "REDIS", "REDIS_URL", "redis://127.0.0.1:1"));
        CacheManager manager = new CacheManager(tempCacheDir, unreachableRedis);
        ChatCacheParameter parameter = new ChatCacheParameter("m", 1, 0.0);

        JedisConnectionException exception = assertThrows(JedisConnectionException.class, () -> manager.getCache(parameter));
        assertTrue(exception.getMessage().contains("127.0.0.1:1"), "the connection must use the injected REDIS_URL");
    }

    /** A parameter with a constant identifier and default (identity) equality, used to force a name conflict. */
    private static final class ConstantParameter implements CacheParameter<ChatCacheKey> {
        @Override
        public String parameters() {
            return "constant";
        }

        @Override
        public LargeLanguageModelCacheMode mode() {
            return LargeLanguageModelCacheMode.CHAT;
        }

        @Override
        public ChatCacheKey createCacheKey(String content) {
            return new ChatCacheParameter("m", 1, 0.0).createCacheKey(content);
        }
    }

    /** A parameter that only differs in mode and identifier, used to check that modes get separate caches. */
    private record ModeParameter(LargeLanguageModelCacheMode mode, String parameters) implements CacheParameter<ChatCacheKey> {
        @Override
        public ChatCacheKey createCacheKey(String content) {
            return new ChatCacheParameter("m", 1, 0.0).createCacheKey(content);
        }
    }
}
