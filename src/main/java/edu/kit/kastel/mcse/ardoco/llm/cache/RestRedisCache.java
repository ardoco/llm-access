/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.cache;

import org.fuchss.restredis.client.Client;
import org.fuchss.restredis.client.ClientConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider;

/**
 * Implements a Redis-based cache for storing and retrieving values using a REST interface.
 *
 * @param <K> The type of cache key used in this cache
 */
/*package-private*/ class RestRedisCache<K extends CacheKey> extends RedisCache<K> {

    /**
     * Creates a new Rest Redis cache instance.
     * This constructor will throw an exception if Rest Redis is unavailable.
     *
     * @param cacheParameter The cache parameter configuration
     * @param mapper         The ObjectMapper for JSON operations
     * @param environment    The environment to read the REST Redis connection settings from
     * @throws IllegalStateException If Redis connection cannot be established
     */
    /*package-private*/ RestRedisCache(CacheParameter<K> cacheParameter, ObjectMapper mapper, EnvironmentProvider environment) {
        super(cacheParameter, mapper, createRedisConnection(environment));
    }

    /**
     * Initiates the REST Redis connection using variables of the given environment for configuration. The following environment variables are used:
     * <ul>
     * <li>{@code REST_REDIS_URI}: The URI of the REST Redis server (default: {@code http://localhost:8080})</li>
     * <li>{@code REST_REDIS_USERNAME}: The username for authentication (optional)</li>
     * <li>{@code REST_REDIS_PASSWORD}: The password for authentication (optional)</li>
     * </ul>
     *
     * @param environment The environment to read the connection settings from
     * @return The connected REST Redis client
     * @throws IllegalStateException If the REST Redis server cannot be reached
     */
    private static UnifiedRedisClient createRedisConnection(EnvironmentProvider environment) {
        String restRedisUri = "http://localhost:8080";
        String restRedisUriEnv = environment.getenv("REST_REDIS_URI");
        if (restRedisUriEnv != null && !restRedisUriEnv.isBlank()) {
            restRedisUri = restRedisUriEnv;
        }
        String restRedisUsername = environment.getenv("REST_REDIS_USERNAME");
        if (restRedisUsername != null && restRedisUsername.isBlank()) {
            restRedisUsername = null;
        }
        String restRedisPassword = environment.getenv("REST_REDIS_PASSWORD");
        if (restRedisPassword != null && restRedisPassword.isBlank()) {
            restRedisPassword = null;
        }

        ClientConfiguration config = new ClientConfiguration(restRedisUri, restRedisUsername, restRedisPassword);
        UnifiedRedisClient redis = new RestRedisAdapter(new Client(config));

        // Check if connection is working
        if (!redis.ping()) {
            redis.close();
            throw new IllegalStateException("Could not connect to Redis at " + restRedisUri);
        }

        return redis;
    }
}
