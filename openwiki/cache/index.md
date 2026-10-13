# Files

- [Cache Backends](cache-backends.md) - The LocalCache, RedisCache, and RestRedisCache backends and the UnifiedRedisClient seam that lets one Cache implementation serve both the Jedis and the REST-Redis transport.
- [Cache Core](cache-core.md) - The Cache interface with its factory and JSON conversion, the CacheKey jsonKey/localKey split, CacheParameter, CacheType, and LargeLanguageModelCacheMode — the storage abstractions all chat and embedding caching builds on.
- [Cache Hierarchy and Manager](cache-hierarchy.md) - CacheManager owns the default cache manager, derives cache names, and builds the HierarchicalCache layering over the cache backends; CacheReplacementStrategy resolves conflicts between layers with write-through semantics and backfill.
- [Cache Keys](cache-keys.md) - Typed cache keying for the chat and embedding caches — ChatCacheKey/ChatCacheParameter, EmbeddingCacheKey/EmbeddingCacheParameter, the KeyGenerator content UUIDs behind localKey, LiSSA backward-compatible file identifiers, and the deprecated EmbeddingCacheKey.ofRaw path.
