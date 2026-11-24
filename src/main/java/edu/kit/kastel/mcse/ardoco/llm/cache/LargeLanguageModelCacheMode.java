/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

/**
 * Defines the possible modes of operation that can be cached.
 */
public enum LargeLanguageModelCacheMode {
    /**
     * LargeLanguageModelCacheMode for caching embedding generation operations.
     */
    EMBEDDING("Embedding"),

    /**
     * LargeLanguageModelCacheMode for caching chat-based operations.
     */
    CHAT("Chat");

    private final String cacheNamePrefix;

    LargeLanguageModelCacheMode(String cacheNamePrefix) {
        this.cacheNamePrefix = cacheNamePrefix;
    }

    /**
     * Returns the prefix of the names of caches of this mode, e.g., of their file names.
     *
     * @return The cache name prefix, {@code Chat} or {@code Embedding}
     */
    public String cacheNamePrefix() {
        return cacheNamePrefix;
    }
}
