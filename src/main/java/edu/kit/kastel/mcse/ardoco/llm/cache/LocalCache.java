/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implements a local file-based cache for storing key-value pairs.
 * This class provides a thread-safe implementation of a cache that persists its contents
 * to a JSON file. It includes automatic flushing of changes when a certain threshold
 * of modifications is reached.
 *
 * @param <K> The type of cache key used in this cache
 */
class LocalCache<K extends CacheKey> implements Cache<K> {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private static final Logger logger = LoggerFactory.getLogger(LocalCache.class);

    /**
     * All local caches by normalized file path, so that one file is backed by exactly one instance.
     */
    private static final Map<Path, LocalCache<?>> INSTANCES = new ConcurrentHashMap<>();

    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean();

    /**
     * Maximum number of modifications before automatic flush.
     */
    private static final int MAX_DIRTY = 50;

    private final CacheParameter<K> cacheParameter;

    /**
     * Counter for unflushed modifications.
     */
    private int dirty = 0;

    private final File cacheFile;

    /**
     * In-memory cache storage.
     */
    private Map<String, String> cache = new HashMap<>();

    /**
     * Creates a new local cache instance.
     * The cache will be initialized from the specified file if it exists,
     * or a new file will be created.
     *
     * @param cacheFile      The path to the cache file
     * @param cacheParameter The cache parameter configuration
     */
    private LocalCache(String cacheFile, CacheParameter<K> cacheParameter) {
        this.cacheParameter = Objects.requireNonNull(cacheParameter);
        this.cacheFile = new File(Objects.requireNonNull(cacheFile));
        createLocalStore();
    }

    /**
     * Returns the local cache for the given file. All callers asking for the same file share one instance.
     *
     * @param <K>            The type of cache key
     * @param cacheFile      The path to the cache file
     * @param cacheParameter The cache parameter configuration
     * @return The shared cache instance of this file
     * @throws IllegalArgumentException If the file is already in use with different parameters
     */
    @SuppressWarnings("unchecked")
    static <K extends CacheKey> LocalCache<K> forFile(String cacheFile, CacheParameter<K> cacheParameter) {
        registerShutdownHook();
        Path path = Path.of(Objects.requireNonNull(cacheFile)).toAbsolutePath().normalize();
        LocalCache<?> existing = INSTANCES.compute(path, (p, current) -> {
            if (current == null) {
                return new LocalCache<>(cacheFile, cacheParameter);
            }
            if (!current.cacheParameter.equals(cacheParameter)) {
                throw new IllegalArgumentException("Cache file " + path + " already exists with different parameters");
            }
            return current;
        });
        return (LocalCache<K>) existing;
    }

    private static void registerShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(LocalCache::flushAll, "llm-access-cache-flush"));
        }
    }

    /**
     * Writes the pending entries of every local cache to disk. A failing cache is logged and does not stop the others.
     */
    static void flushAll() {
        for (LocalCache<?> cache : INSTANCES.values()) {
            try {
                cache.flush();
            } catch (RuntimeException e) {
                logger.error("Could not flush cache file {}", cache.cacheFile, e);
            }
        }
    }

    /**
     * Checks if the cache is ready for use.
     * This method ensures that the cache file exists and is accessible.
     *
     * @return true if the cache is ready, false otherwise
     * @throws UncheckedIOException If there are issues accessing the cache file
     */
    public boolean isReady() {
        try {
            return cacheFile.exists() || cacheFile.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Initializes the local cache store.
     * If the cache file exists and is not empty, its contents are loaded into memory.
     * If the file is empty, it is deleted to ensure a clean state.
     *
     * @throws IllegalArgumentException If the cache file cannot be read
     */
    private void createLocalStore() {
        if (cacheFile.exists()) {
            try {
                if (Files.readString(cacheFile.toPath()).isBlank()) {
                    cacheFile.delete();
                } else {
                    cache = MAPPER.readValue(cacheFile, new TypeReference<>() {
                    });
                }
            } catch (IOException | JacksonException e) {
                throw new IllegalArgumentException("Could not read cache file (" + cacheFile.getName() + ")", e);
            }
        }
    }

    /**
     * Writes the current cache contents to disk.
     * This method uses a temporary file to ensure atomic writes and prevent data corruption.
     * The dirty counter is reset after a successful write.
     *
     * @throws IllegalArgumentException If the cache file cannot be written
     */
    public synchronized void write() {
        if (dirty == 0) {
            return;
        }

        try {
            File tempFile = new File(cacheFile.getAbsolutePath() + ".tmp.json");
            MAPPER.writeValue(tempFile, cache);
            Files.copy(tempFile.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempFile.toPath());
            dirty = 0;
        } catch (IOException | JacksonException e) {
            throw new IllegalArgumentException("Could not write cache file", e);
        }
    }

    @Override
    public synchronized <T> @Nullable T get(String key, Class<T> clazz) {
        K cacheKey = cacheParameter.createCacheKey(key);
        String jsonData = cache.get(cacheKey.localKey());
        return Cache.convert(jsonData, clazz, MAPPER);
    }

    /**
     * Retrieves a value from the cache.
     *
     * @param key The cache key to look up
     * @return The cached value, or null if not found
     * @deprecated This method exposes internal cache key handling and should not be used in general code.
     */
    @Override
    @Deprecated(forRemoval = false)
    public synchronized <T> @Nullable T getViaInternalKey(K key, Class<T> clazz) {
        String jsonData = cache.get(key.localKey());
        return Cache.convert(jsonData, clazz, MAPPER);
    }

    @Override
    public synchronized void put(String key, String value) {
        K cacheKey = cacheParameter.createCacheKey(key);
        putViaInternalKey(cacheKey, value);
    }

    /**
     * Stores a value in the cache.
     * If the value is different from the existing value (if any), the dirty counter is incremented.
     * If the dirty counter exceeds the maximum threshold, the cache is automatically flushed to disk.
     *
     * @param cacheKey The cache key to store the value under
     * @param value    The value to store
     * @deprecated This method exposes internal cache key handling and should not be used in general code.
     */
    @Override
    @Deprecated(forRemoval = false)
    public synchronized <T> void putViaInternalKey(K cacheKey, T value) {
        String jsonValue;
        try {
            jsonValue = MAPPER.writeValueAsString(Objects.requireNonNull(value));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not serialize object", e);
        }
        String old = cache.put(cacheKey.localKey(), jsonValue);
        if (old == null || !old.equals(jsonValue)) {
            dirty++;
        }

        if (dirty > MAX_DIRTY) {
            write();
        }
    }

    @Override
    public synchronized <T> void put(String key, T value) {
        K cacheKey = cacheParameter.createCacheKey(key);
        putViaInternalKey(cacheKey, value);
    }

    @Override
    public void flush() {
        write();
    }

    /**
     * Returns true if and only if this map contains a mapping for a key
     *
     * @param key The cache key to look up
     * @return true if this map contains a mapping for the specified key
     */
    @Override
    public synchronized boolean containsKey(String key) {
        K cacheKey = cacheParameter.createCacheKey(key);
        return cache.containsKey(cacheKey.localKey());
    }

    @Override
    public CacheParameter<K> getCacheParameter() {
        return this.cacheParameter;
    }
}
