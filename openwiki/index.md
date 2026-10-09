---
okf_version: "0.1"
---

# Files

- [Cache Backends](cache-backends.md) - The LocalCache, RedisCache, and RestRedisCache backends, and the UnifiedRedisClient abstraction over Jedis and the REST-Redis HTTP client.
- [Cache Core](cache-core.md) - The Cache interface, CacheKey, CacheParameter, CacheType, and LargeLanguageModelCacheMode abstractions that all chat and embedding caching build on.
- [Cache Hierarchy and Manager](cache-hierarchy.md) - CacheManager owns the default cache manager and builds the HierarchicalCache layering, applying CacheReplacementStrategy on read conflicts; backends are stacked primary-first with write-through semantics and backfill.
- [Cache Keys](cache-keys.md) - The ChatCacheKey and ChatCacheParameter and EmbeddingCacheKey and EmbeddingCacheParameter implementations of CacheKey and CacheParameter, including backward-compatible file identifiers and the deprecated EmbeddingCacheKey.ofRaw.
- [Cached Chat Requests](cached-chat.md) - How CachingChatModel decorates a ChatModel to cache responses transparently, and how ChatModelUtils provides single and n-fold cached request helpers.
- [Chat Models](chat-models.md) - How ChatModelProvider creates lazily-initialized LangChain4j ChatModel instances for OpenAI, Ollama, Blablador, DeepSeek, and Open WebUI from an LlmConfiguration, and how LlmConfiguration and ChatModelPlatform model chat configuration.
- [Configuration and Environment](configuration.md) - Environment loads .env over system env vars and is the single source of credentials and hosts; KeyGenerator produces stable content UUIDs for cache keys; Futures resolves parallel embedding futures.
- [Embeddings](embeddings.md) - The EmbeddingCreator hierarchy for OpenAI, Ollama, ONNX, Open WebUI, and Mock embeddings, including CachedEmbeddingCreator caching, parallel execution, and long-text recovery.
- [Operations and Deployment](operations.md) - Deploying the LOCAL, Redis, and REST-Redis cache backends, building replication packages, the fail-fast connection contract, and the CI/release workflows that build and publish the library.
- [ARDoCo LLM Access — Quickstart](quickstart.md) - Entry point to the OpenWiki knowledge base for the llm-access library; maps change areas to wiki pages, source entry points, key symbols, focused tests, and validation commands.
