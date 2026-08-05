/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.kit.kastel.mcse.ardoco.llm.util.KeyGenerator;

/**
 * Comprehensive tests for CacheReplacementStrategy enum implementations.
 * These tests verify correct conflict resolution behavior with various data types
 * including strings, objects, and null values.
 */
@NullMarked
class CacheReplacementStrategyTest {
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";
    private static final TestObject TEST_OBJECT = new TestObject("test", 42);
    private static final String CONFLICTING_VALUE = "conflicting-value";

    @TempDir
    private Path tempCacheDir;

    private Cache<TestCacheKey> primaryCache;
    private Cache<TestCacheKey> secondaryCache;
    private TestCacheKey cacheKeyInstance;

    @BeforeEach
    void setUp() {
        primaryCache = createLocalCache("primary");
        secondaryCache = createLocalCache("secondary");
        cacheKeyInstance = TestCacheKey.of(new TestCacheParameter(), "test");
    }

    /**
     * Factory method to create a LocalCache instance for testing
     */
    private Cache<TestCacheKey> createLocalCache(String cachePrefix) {
        return new LocalCache<>(tempCacheDir.resolve(cachePrefix + "_cache.json").toString(), new TestCacheParameter());
    }

    // ==================== NONE Strategy String Tests ====================

    @Test
    @DisplayName("NONE strategy: with string values - identical")
    void testNoneStrategyStringIdentical() {
        primaryCache.put(TEST_KEY, TEST_VALUE);
        secondaryCache.put(TEST_KEY, TEST_VALUE);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(TEST_VALUE, secondaryCache.get(TEST_KEY, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.NONE;
        String primaryValue = primaryCache.get(TEST_KEY, String.class);
        String secondaryValue = secondaryCache.get(TEST_KEY, String.class);

        String result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(TEST_VALUE, secondaryCache.get(TEST_KEY, String.class));
    }

    @Test
    @DisplayName("NONE strategy: with string values - conflicting")
    void testNoneStrategyStringConflicting() {
        primaryCache.put(TEST_KEY, TEST_VALUE);
        secondaryCache.put(TEST_KEY, CONFLICTING_VALUE);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(CONFLICTING_VALUE, secondaryCache.get(TEST_KEY, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.NONE;
        String primaryValue = primaryCache.get(TEST_KEY, String.class);
        String secondaryValue = secondaryCache.get(TEST_KEY, String.class);

        String result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(CONFLICTING_VALUE, secondaryCache.get(TEST_KEY, String.class));
    }

    @Test
    @DisplayName("NONE strategy: with null primary")
    void testNoneStrategyNullPrimary() {
        secondaryCache.put(TEST_KEY, TEST_VALUE);
        assertNull(primaryCache.get(TEST_KEY, String.class));
        assertEquals(TEST_VALUE, secondaryCache.get(TEST_KEY, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.NONE;
        String primaryValue = primaryCache.get(TEST_KEY, String.class);
        String secondaryValue = secondaryCache.get(TEST_KEY, String.class);

        String result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(TEST_VALUE, secondaryCache.get(TEST_KEY, String.class));
    }

    @Test
    @DisplayName("NONE strategy: with object values - deep equal but different instances")
    void testNoneStrategyObjectDeepEqual() {
        TestObject obj1 = new TestObject("test", 42);
        TestObject obj2 = new TestObject("test", 42);

        primaryCache.put(TEST_KEY, obj1);
        secondaryCache.put(TEST_KEY, obj2);
        assertEquals(obj1, primaryCache.get(TEST_KEY, TestObject.class));
        assertEquals(obj2, secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.NONE;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(obj1, result);
        assertEquals(obj2, result);
    }

    // ==================== ERROR Strategy Conflict Tests ====================

    @Test
    @DisplayName("ERROR strategy: throws when string values conflict")
    void testErrorStrategyStringConflict() {
        primaryCache.put(TEST_KEY, TEST_VALUE);
        secondaryCache.put(TEST_KEY, CONFLICTING_VALUE);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertEquals(CONFLICTING_VALUE, secondaryCache.get(TEST_KEY, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.ERROR;
        String primaryValue = primaryCache.get(TEST_KEY, String.class);
        String secondaryValue = secondaryCache.get(TEST_KEY, String.class);

        assertThrows(IllegalStateException.class, () -> strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache));
    }

    @Test
    @DisplayName("ERROR strategy: tolerates null vs non-null in different layers")
    void testErrorStrategyNullTolerance() {
        primaryCache.put(TEST_KEY, TEST_VALUE);
        assertEquals(TEST_VALUE, primaryCache.get(TEST_KEY, String.class));
        assertNull(secondaryCache.get(TEST_KEY, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.ERROR;
        String primaryValue = primaryCache.get(TEST_KEY, String.class);
        String secondaryValue = secondaryCache.get(TEST_KEY, String.class);

        String result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
    }

    @Test
    @DisplayName("ERROR strategy: accepts identical objects")
    void testErrorStrategyIdenticalObjects() {
        TestObject obj1 = new TestObject("test", 42);
        TestObject obj2 = new TestObject("test", 42);

        primaryCache.put(TEST_KEY, obj1);
        secondaryCache.put(TEST_KEY, obj2);
        assertEquals(obj1, primaryCache.get(TEST_KEY, TestObject.class));
        assertEquals(obj2, secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.ERROR;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(obj1, result);
    }

    // ==================== OVERWRITE Strategy Tests ====================

    @Test
    @DisplayName("OVERWRITE strategy: overwrites secondary on conflict")
    void testOverwriteStrategyObjectConflict() {
        TestObject secondary = new TestObject("secondary", 2);

        primaryCache.put(TEST_KEY, TEST_OBJECT);
        secondaryCache.put(TEST_KEY, secondary);
        assertEquals(TEST_OBJECT, primaryCache.get(TEST_KEY, TestObject.class));
        assertEquals(secondary, secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_OBJECT, result);
        assertEquals(TEST_OBJECT, secondaryCache.get(TEST_KEY, TestObject.class));
    }

    @Test
    @DisplayName("OVERWRITE strategy: does not overwrite when values are identical")
    void testOverwriteStrategyNoOverwriteOnIdentical() {
        primaryCache.put(TEST_KEY, TEST_OBJECT);
        secondaryCache.put(TEST_KEY, TEST_OBJECT);
        assertEquals(TEST_OBJECT, primaryCache.get(TEST_KEY, TestObject.class));
        assertEquals(TEST_OBJECT, secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_OBJECT, result);
    }

    @Test
    @DisplayName("OVERWRITE strategy: does backfill when secondary is null")
    void testOverwriteStrategyNoOverwriteWhenSecondaryNull() {
        primaryCache.put(TEST_KEY, TEST_OBJECT);
        assertEquals(TEST_OBJECT, primaryCache.get(TEST_KEY, TestObject.class));
        assertNull(secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_OBJECT, result);
        assertEquals(TEST_OBJECT, secondaryCache.get(TEST_KEY, TestObject.class));
    }

    @Test
    @DisplayName("OVERWRITE strategy: handles null primary with non-null secondary")
    void testOverwriteStrategyNullPrimary() {
        secondaryCache.put(TEST_KEY, TEST_OBJECT);
        assertNull(primaryCache.get(TEST_KEY, TestObject.class));
        assertEquals(TEST_OBJECT, secondaryCache.get(TEST_KEY, TestObject.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        TestObject primaryValue = primaryCache.get(TEST_KEY, TestObject.class);
        TestObject secondaryValue = secondaryCache.get(TEST_KEY, TestObject.class);

        TestObject result = strategy.resolve(TEST_KEY, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_OBJECT, result);
        assertEquals(TEST_OBJECT, primaryCache.get(TEST_KEY, TestObject.class));
    }

    // ==================== ViaInternalKey Strategy Tests ====================

    @Test
    @DisplayName("NONE strategy via internal key: string backfill to primary when secondary has value")
    void testNoneStrategyViaInternalKeyStringBackfillPrimary() {
        secondaryCache.putViaInternalKey(cacheKeyInstance, TEST_VALUE);
        assertNull(primaryCache.getViaInternalKey(cacheKeyInstance, String.class));
        assertEquals(TEST_VALUE, secondaryCache.getViaInternalKey(cacheKeyInstance, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.NONE;
        String primaryValue = primaryCache.getViaInternalKey(cacheKeyInstance, String.class);
        String secondaryValue = secondaryCache.getViaInternalKey(cacheKeyInstance, String.class);

        String result = strategy.resolveViaInternalKey(cacheKeyInstance, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, primaryCache.getViaInternalKey(cacheKeyInstance, String.class));
        assertEquals(TEST_VALUE, secondaryCache.getViaInternalKey(cacheKeyInstance, String.class));
    }

    @Test
    @DisplayName("OVERWRITE strategy via internal key: string overwrite of secondary cache")
    void testOverwriteStrategyViaInternalKeyStringConflict() {
        primaryCache.putViaInternalKey(cacheKeyInstance, TEST_VALUE);
        secondaryCache.putViaInternalKey(cacheKeyInstance, CONFLICTING_VALUE);
        assertEquals(TEST_VALUE, primaryCache.getViaInternalKey(cacheKeyInstance, String.class));
        assertEquals(CONFLICTING_VALUE, secondaryCache.getViaInternalKey(cacheKeyInstance, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        String primaryValue = primaryCache.getViaInternalKey(cacheKeyInstance, String.class);
        String secondaryValue = secondaryCache.getViaInternalKey(cacheKeyInstance, String.class);

        String result = strategy.resolveViaInternalKey(cacheKeyInstance, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, secondaryCache.getViaInternalKey(cacheKeyInstance, String.class));
    }

    @Test
    @DisplayName("OVERWRITE strategy via internal key: string backfill to secondary when primary is null")
    void testOverwriteStrategyViaInternalKeyStringBackfillSecondary() {
        secondaryCache.putViaInternalKey(cacheKeyInstance, TEST_VALUE);
        assertNull(primaryCache.getViaInternalKey(cacheKeyInstance, String.class));
        assertEquals(TEST_VALUE, secondaryCache.getViaInternalKey(cacheKeyInstance, String.class));

        CacheReplacementStrategy strategy = CacheReplacementStrategy.OVERWRITE;
        String primaryValue = primaryCache.getViaInternalKey(cacheKeyInstance, String.class);
        String secondaryValue = secondaryCache.getViaInternalKey(cacheKeyInstance, String.class);

        String result = strategy.resolveViaInternalKey(cacheKeyInstance, primaryValue, primaryCache, secondaryValue, secondaryCache);

        assertEquals(TEST_VALUE, result);
        assertEquals(TEST_VALUE, primaryCache.getViaInternalKey(cacheKeyInstance, String.class));
    }

    // ==================== Helper Classes ====================

    static class TestObject {
        public String name = "";
        public int value;

        @SuppressWarnings("unused")
        TestObject() {
            // For Jackson deserialization
        }

        TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof TestObject that))
                return false;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return "TestObject{" + "name='" + name + '\'' + ", value=" + value + '}';
        }
    }

    static class TestCacheKey implements CacheKey {
        private final String keyValue;

        private TestCacheKey(String content) {
            this.keyValue = KeyGenerator.generateKey(content);
        }

        static TestCacheKey of(CacheParameter<TestCacheKey> cacheParameter, String content) {
            // Access cacheParameter to satisfy architecture test requirements
            String parameters = cacheParameter.parameters();
            return new TestCacheKey(content + parameters);
        }

        @Override
        public String localKey() {
            return keyValue;
        }

        @Override
        public String toString() {
            return keyValue;
        }
    }

    static class TestCacheParameter implements CacheParameter<TestCacheKey> {
        @Override
        public String parameters() {
            return "test-cache";
        }

        @Override
        public TestCacheKey createCacheKey(String content) {
            return TestCacheKey.of(this, content);
        }
    }
}
