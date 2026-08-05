/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

/**
 * Defines the possible modes of operation that can be cached.
 */
public enum LargeLanguageModelCacheMode {
    /**
     * LargeLanguageModelCacheMode for caching embedding generation operations.
     */
    EMBEDDING,

    /**
     * LargeLanguageModelCacheMode for caching chat-based operations.
     */
    CHAT
}
