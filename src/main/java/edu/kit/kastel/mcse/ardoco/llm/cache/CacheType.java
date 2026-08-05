/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

/**
 * Enum representing the types of caches supported by the system.
 */
public enum CacheType {
    /**
     * File based local cache
     */
    LOCAL,
    /**
     * Redis based local docker container for caching
     */
    REDIS,
    /**
     * Remote Redis instance accessible via a REST API
     */
    REST_REDIS
}
