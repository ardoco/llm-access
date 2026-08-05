---
type: Overview
title: ARDoCo LLM Access — Quickstart
description: Entry point to the OpenWiki knowledge base for the llm-access library; maps change areas to wiki pages, source entry points, key symbols, focused tests, and validation commands.
tags: [quickstart, overview]
openwiki:
  roles: [architecture, repository]
  source_paths: [README.md, pom.xml]
  validation_commands: ["mvn -q -DskipTests=false test"]
---

# ARDoCo LLM Access

`llm-access` is a small, reusable Java 21 library for accessing Large Language Models (LLMs)
and embedding models through [LangChain4j](https://docs.langchain4j.dev/), with a pluggable
caching layer for LLM requests and embeddings. It is framework-neutral: model settings are
passed as plain configuration objects, while credentials and hosts are read from the
environment (`.env` or system env vars). It was extracted from the [LiSSA](https://github.com/ardoco/lissa)
project so LiSSA, [ardoco](https://github.com/ardoco), and other tools can share one
implementation. Coordinates: `io.github.ardoco:llm-access` (see `pom.xml`).

Caching is central to how the library is meant to be used: with a fixed `seed` and
`temperature`, identical requests are served from the cache instead of being re-sent to the
model, which makes runs reproducible and keeps API cost and latency down. See
[Cache Core](cache-core.md) and [Cache Hierarchy and Manager](cache-hierarchy.md).

## How the wiki is organized

| Area | Page | What it covers |
| --- | --- | --- |
| Chat models | [Chat Models](chat-models.md) | `ChatModelProvider`, platforms, `LlmConfiguration`, `LazyChatModel` |
| Cached chat | [Cached Chat Requests](cached-chat.md) | `CachingChatModel` decorator and `ChatModelUtils` |
| Embeddings | [Embeddings](embeddings.md) | `EmbeddingCreator` hierarchy, parallelism, long-text recovery |
| Cache core | [Cache Core](cache-core.md) | `Cache`, `CacheKey`, `CacheParameter`, `CacheType`, modes |
| Cache hierarchy | [Cache Hierarchy and Manager](cache-hierarchy.md) | `CacheManager`, `HierarchicalCache`, `CacheReplacementStrategy` |
| Cache backends | [Cache Backends](cache-backends.md) | `LocalCache`, `RedisCache`, `RestRedisCache`, `UnifiedRedisClient` |
| Cache keys | [Cache Keys](cache-keys.md) | `ChatCacheKey`/`ChatCacheParameter`, `EmbeddingCacheKey`/`EmbeddingCacheParameter` |
| Configuration | [Configuration and Environment](configuration.md) | `Environment`, `KeyGenerator`, `Futures`, env vars |
| Operations | [Operations and Deployment](operations.md) | Redis/REST-Redis deployment, replication packages, CI |

## Task routing table

| Change area / intent | Page | Source entry points | Key symbols / types | Focused tests | Minimal validation |
| --- | --- | --- | --- | --- | --- |
| Add or change a chat platform | [Chat Models](chat-models.md) | `chat/ChatModelProvider.java`, `chat/ChatModelPlatform.java` | `ChatModelProvider.createChatModel`, `ChatModelPlatform` | `ChatModelProviderTest`, `ChatConfigurationTest` | `mvn -q test -Dtest=ChatModelProviderTest,ChatConfigurationTest` |
| Change cached chat behavior | [Cached Chat Requests](cached-chat.md) | `chat/CachingChatModel.java`, `chat/ChatModelUtils.java` | `CachingChatModel.doChat`, `ChatModelUtils.nCachedRequest` | `CachingChatModelTest`, `ChatModelUtilsTest` | `mvn -q test -Dtest=CachingChatModelTest,ChatModelUtilsTest` |
| Add or change an embedding platform | [Embeddings](embeddings.md) | `embedding/EmbeddingCreator.java`, `embedding/*EmbeddingCreator.java` | `EmbeddingCreator.create`, `CachedEmbeddingCreator.calculateEmbeddings` | `CachedEmbeddingCreatorTest`, `EmbeddingConfigurationTest` | `mvn -q test -Dtest=CachedEmbeddingCreatorTest,EmbeddingConfigurationTest` |
| Change cache abstraction / key model | [Cache Core](cache-core.md), [Cache Keys](cache-keys.md) | `cache/Cache.java`, `cache/CacheKey.java`, `cache/chat/*`, `cache/embedding/*` | `Cache`, `CacheKey`, `CacheParameter`, `ChatCacheParameter` | `CacheTest`, `ChatCacheKeyTest`, `EmbeddingCacheKeyTest` | `mvn -q test -Dtest=CacheTest,ChatCacheKeyTest,EmbeddingCacheKeyTest` |
| Change layering / conflict strategy | [Cache Hierarchy and Manager](cache-hierarchy.md) | `cache/CacheManager.java`, `cache/HierarchicalCache.java`, `cache/CacheReplacementStrategy.java` | `CacheManager`, `HierarchicalCache`, `CacheReplacementStrategy` | `CacheManagerTest`, `HierarchicalCacheTest`, `CacheReplacementStrategyTest` | `mvn -q test -Dtest=CacheManagerTest,HierarchicalCacheTest,CacheReplacementStrategyTest` |
| Add or change a cache backend | [Cache Backends](cache-backends.md) | `cache/LocalCache.java`, `cache/RedisCache.java`, `cache/RestRedisCache.java`, `cache/UnifiedRedisClient.java` | `LocalCache`, `RedisCache`, `RestRedisCache`, `UnifiedRedisClient` | `CacheTest`, `RestRedisTest` | `mvn -q test -Dtest=CacheTest` (REST-Redis needs Docker) |
| Change env/credential handling | [Configuration and Environment](configuration.md) | `util/Environment.java`, `util/KeyGenerator.java`, `util/Futures.java` | `Environment`, `KeyGenerator.generateKey`, `Futures.getLogged` | `EnvironmentTest`, `KeyGeneratorTest`, `FuturesTest` | `mvn -q test -Dtest=EnvironmentTest,KeyGeneratorTest,FuturesTest` |
| Deployment / CI / release | [Operations and Deployment](operations.md) | `README.md`, `.github/workflows/*`, `pom.xml` | `central-publishing-maven-plugin`, REST-Redis image | — | see workflows |

## Build and test

```bash
mvn verify
```

The Testcontainers-based REST-Redis integration test ([Operations and Deployment](operations.md))
is skipped automatically when no Docker environment is available.

## Backlog

None. Initial coverage is complete.
