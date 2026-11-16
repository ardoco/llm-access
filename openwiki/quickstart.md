---
type: Overview
title: ARDoCo LLM Access — Quickstart
description: Entry point to the OpenWiki knowledge base for the llm-access library; one-paragraph system summary, the wiki map over the final page hierarchy, and a task-routing table that maps change areas to pages, source entry points, key symbols, focused tests, and minimal validation commands.
tags: [quickstart, overview, routing, caching]
openwiki:
  roles: [architecture, repository]
  source_paths: [.github/workflows/verify.yml, AGENTS.md, README.md, pom.xml, sample.env]
  validation_commands: ["mvn verify"]
verified:
  - by: openwiki/0.9.0
    at: 2026-11-16T14:46:55.207Z
sources:
  - id: openwiki-source-4320a40cc58d6de85374b359
    resource: repo://.github/workflows/verify.yml
  - id: openwiki-source-2355f81d7cf522f8dbdaabd4
    resource: repo://pom.xml
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-4fd2e823addcf84d995a9216
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManager.java
  - id: openwiki-source-ba0dc6c01c930c4c675be60f
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/chat/ChatCacheKey.java
  - id: openwiki-source-afc251b7258caad3fb7240df
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/chat/ChatCacheParameter.java
  - id: openwiki-source-f933314cddd3af5c44148dcf
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/EnvironmentProvider.java
  - id: openwiki-source-91e90e06abef694a92ea2a06
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/KeyGenerator.java
  - id: openwiki-source-30f99b58023b3a9a8c01b6d4
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManagerTest.java
  - id: openwiki-source-80764162ff1e0089fe344077
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheTest.java
  - id: openwiki-source-77fcb76d829af13337d04708
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
generated: { by: "openwiki/0.9.0", at: "2026-11-16T14:46:55.207Z" }
---

# ARDoCo LLM Access

`llm-access` (`io.github.ardoco:llm-access`, artifact version `0.2.2-SNAPSHOT`, see `pom.xml`) is a small, reusable Java 21 library for
accessing Large Language Models (LLMs) and embedding models through
[LangChain4j](https://docs.langchain4j.dev/) (imported as the LangChain4j **1.23.0 BOM**), extracted from
the [LiSSA](https://github.com/ardoco/lissa) project so LiSSA, [ardoco](https://github.com/ardoco),
and other tools can share one implementation. It is framework-neutral: model settings are passed as plain
configuration records (`LlmConfiguration`, `EmbeddingConfiguration`) plus an injected `EnvironmentProvider`
that supplies credentials and hosts (system environment variables with a lazy `.env` fallback, or an
in-memory `MapEnvironment`) — there is **no global environment**. Caching is central to how the library is
meant to be used: a `CacheManager` hands out `Cache` instances backed by local files, Redis, or REST-Redis,
layered according to `CACHE_HIERARCHY`; with a fixed `seed` and `temperature`, identical requests are served
from the cache instead of being re-sent to the model, which makes runs reproducible and keeps API cost and
latency down. The on-disk format is compatible with LiSSA's existing caches. See
[Architecture Overview](architecture/overview.md) and [Cache Core](cache/cache-core.md).

## Toolchain at a glance

- **Artifact & JVM:** `io.github.ardoco:llm-access` `0.2.2-SNAPSHOT` under the `io.github.ardoco:parent`
  `2.2.0-SNAPSHOT`; Java 21 (`README.md` "Requirements"; the CI workflows derive the JDK major version from
  the POM rather than pinning it).
- **Model access:** LangChain4j **1.23.0** via the `langchain4j-bom` import — `langchain4j`,
  `langchain4j-core`, `langchain4j-embeddings`, `langchain4j-ollama`, and `langchain4j-open-ai`.
- **JSON:** Jackson 3 core (`tools.jackson.core:jackson-core` / `jackson-databind`, built with
  `JsonMapper.builderWithJackson2Defaults()` for the byte-stable cache-key format) alongside
  `com.fasterxml.jackson.core:jackson-annotations` 2.x, which supplies the annotations on the cache keys.
- **Cache transport & text handling:** `org.fuchss:rest-redis` 0.1.7 (REST-Redis client),
  `com.knuddels:jtokkit` 1.1.0 (token counting in the long-text embedding recovery),
  `io.github.cdimascio:dotenv-java` 3.2.0 (`.env` parsing), `org.slf4j:slf4j-api`.
- **Test scope:** `com.tngtech.archunit:archunit-junit5` 1.5.1 and `org.testcontainers:testcontainers` plus
  `testcontainers-junit-jupiter` 2.0.5 (via the `testcontainers-bom`).

## How the wiki is organized

| Page | What it covers |
| --- | --- |
| [Architecture Overview](architecture/overview.md) | The subsystem map: chat, embedding, cache, and util, their one-way dependency direction, the public API surface, and the no-global-environment invariant |
| [Chat Models](chat/chat-models.md) | `ChatModelProvider`, `ChatModelPlatform`, `LlmConfiguration`, `LazyChatModel`, per-platform credentials |
| [Cached Chat Requests](chat/cached-chat.md) | The `CachingChatModel` decorator and the `ChatModelUtils` helpers for explicit cached requests |
| [Embeddings](embeddings/embeddings.md) | The `EmbeddingCreator` factory and platform creators, `EmbeddingConfiguration`, `CachedEmbeddingCreator`'s caching, parallel execution, and long-text recovery |
| [Cache Core](cache/cache-core.md) | `Cache`, `CacheKey`, `CacheParameter`, `CacheType`, `LargeLanguageModelCacheMode` — the storage abstractions |
| [Cache Keys](cache/cache-keys.md) | `ChatCacheKey`/`ChatCacheParameter`, `EmbeddingCacheKey`/`EmbeddingCacheParameter`, `KeyGenerator`, and the LiSSA-compatible file naming |
| [Cache Hierarchy and Manager](cache/cache-hierarchy.md) | `CacheManager`, `HierarchicalCache`, `CacheReplacementStrategy`, write-through and backfill semantics |
| [Cache Backends](cache/cache-backends.md) | `LocalCache`, `RedisCache`, `RestRedisCache`, and the `UnifiedRedisClient` seam |
| [Environment and Configuration](concepts/environment-provider.md) | The `EnvironmentProvider` seam (`SystemEnvironment`, `MapEnvironment`), which variables each subsystem reads, and the no-global-environment invariant |
| [Operations and Deployment](operations/deployment.md) | Deploying LOCAL, Redis, and REST-Redis, the replication-package workflow, the fail-fast connection contract, and the CI/release pipelines |
| [Testing Strategy](testing/strategy.md) | Hermetic unit tests, Mockito layer tests, the Docker-gated `RestRedisTest`, ArchUnit architecture rules, backward-compatibility fixtures, and the formatting gates |

## Task routing table

Entry-point paths are relative to `src/main/java/edu/kit/kastel/mcse/ardoco/llm/` unless written as full
repository paths.

| Change area / intent | Page | Source entry points | Key symbols / types | Focused tests | Minimal validation |
| --- | --- | --- | --- | --- | --- |
| Understand how the parts fit together | [Architecture Overview](architecture/overview.md) | `README.md`, package tree of `src/main/java/…llm` | subsystem dependency direction, no-global-environment invariant | `NoGlobalEnvironmentTest` | `mvn -q test -Dtest=NoGlobalEnvironmentTest` |
| Add or change a chat platform | [Chat Models](chat/chat-models.md) | `chat/ChatModelProvider.java`, `chat/ChatModelPlatform.java`, `chat/LlmConfiguration.java` | `ChatModelProvider.createChatModel`, `ChatModelPlatform`, `LazyChatModel` | `ChatModelProviderTest`, `ChatConfigurationTest`, `LazyChatModelTest` | `mvn -q test -Dtest=ChatModelProviderTest,ChatConfigurationTest,LazyChatModelTest` |
| Change cached chat behavior | [Cached Chat Requests](chat/cached-chat.md) | `chat/CachingChatModel.java`, `chat/ChatModelUtils.java` | `CachingChatModel.doChat`, `ChatModelUtils.cachedRequest`, `ChatModelUtils.nCachedRequest` | `CachingChatModelTest`, `ChatModelUtilsTest` | `mvn -q test -Dtest=CachingChatModelTest,ChatModelUtilsTest` |
| Add or change an embedding platform | [Embeddings](embeddings/embeddings.md) | `embedding/EmbeddingCreator.java`, `embedding/CachedEmbeddingCreator.java`, `embedding/*EmbeddingCreator.java` | `EmbeddingCreator.create`, `CachedEmbeddingCreator.calculateEmbeddings` | `CachedEmbeddingCreatorTest`, `EmbeddingConfigurationTest` | `mvn -q test -Dtest=CachedEmbeddingCreatorTest,EmbeddingConfigurationTest` |
| Change cache abstraction / key rules | [Cache Core](cache/cache-core.md), [Cache Keys](cache/cache-keys.md) | `cache/Cache.java`, `cache/CacheKey.java`, `cache/CacheParameter.java`, `cache/chat/*`, `cache/embedding/*` | `Cache.createByType`, `Cache.convert`, `CacheKey.toJsonKey`, `CacheKey.localKey`, `ChatCacheParameter` | `CacheTest`, `ChatCacheKeyTest`, `EmbeddingCacheKeyTest`, `CacheKeySerializationTest`, `ArchitectureTest` | `mvn -q test -Dtest=CacheTest,ChatCacheKeyTest,EmbeddingCacheKeyTest,CacheKeySerializationTest,ArchitectureTest` |
| Change layering / conflict strategy | [Cache Hierarchy and Manager](cache/cache-hierarchy.md) | `cache/CacheManager.java`, `cache/HierarchicalCache.java`, `cache/CacheReplacementStrategy.java` | `CacheManager.getCache`, `HierarchicalCache`, `CacheReplacementStrategy` | `CacheManagerTest`, `HierarchicalCacheTest`, `CacheReplacementStrategyTest` | `mvn -q test -Dtest=CacheManagerTest,HierarchicalCacheTest,CacheReplacementStrategyTest` |
| Add or change a cache backend | [Cache Backends](cache/cache-backends.md) | `cache/LocalCache.java`, `cache/RedisCache.java`, `cache/RestRedisCache.java`, `cache/UnifiedRedisClient.java` | `LocalCache`, `RedisCache`, `RestRedisCache`, `UnifiedRedisClient`, `RedisAdapter`, `RestRedisAdapter` | `CacheTest`, `RestRedisTest` | `mvn -q test -Dtest=CacheTest` (REST-Redis needs Docker) |
| Change env/credential handling | [Environment and Configuration](concepts/environment-provider.md) | `util/EnvironmentProvider.java`, `util/SystemEnvironment.java`, `util/MapEnvironment.java` | `EnvironmentProvider`, `SystemEnvironment`, `MapEnvironment`, `Bifrost`, `KeyGenerator.generateKey`, `Futures.getLogged` | `SystemEnvironmentTest`, `MapEnvironmentTest`, `KeyGeneratorTest`, `FuturesTest`, `BifrostTest`, `NoGlobalEnvironmentTest` | `mvn -q test -Dtest=SystemEnvironmentTest,MapEnvironmentTest,KeyGeneratorTest,FuturesTest,BifrostTest,NoGlobalEnvironmentTest` |
| Deployment / CI / release | [Operations and Deployment](operations/deployment.md) | `README.md`, `.github/workflows/*`, `pom.xml`, `sample.env` | `CACHE_HIERARCHY`, `CACHE_REPLACEMENT_STRATEGY`, `REDIS_URL`, `REST_REDIS_URI`, `central-publishing-maven-plugin` | `BifrostIntegrationTest` | `BIFROST_API_KEY=… BIFROST_URL=… mvn -q test -Dtest=BifrostIntegrationTest` (needs a live gateway); `mvn verify` otherwise |
| Test approach / build gates | [Testing Strategy](testing/strategy.md) | `src/test/java/**`, `.github/workflows/verify.yml`, `formatter.xml`, `spotless.importorder` | `MapEnvironment`, `@TempDir`, `@Testcontainers(disabledWithoutDocker = true)`, `CacheManager.resetDefaultInstance` | — | `mvn verify` |

## Build and test

```bash
mvn verify
```

`mvn verify` is the full check: the whole unit suite plus the ArchUnit architecture rules, plus the
Testcontainers-based REST-Redis integration test ([Testing Strategy](testing/strategy.md)), which is skipped
automatically when no Docker environment is available, and the live-gated `BifrostIntegrationTest`, which is
skipped unless both `BIFROST_API_KEY` and `BIFROST_URL` are set (in CI, `.github/workflows/bifrost.yml` runs
it against the real gateway and skips forks and Dependabot, where the secrets are unavailable). Focused runs
use the `mvn -q test -Dtest=…` commands from the routing table above. Formatting is enforced via Spotless
(`mvn spotless:apply` locally); the formatting bot applies its result automatically on PRs to `main`
([Operations and Deployment](operations/deployment.md)).

Tests that touch the default `CacheManager` share one static singleton: they call
`CacheManager.setCacheDir(tempDir, environment)` in `@BeforeEach` for a fresh manager backed by an injected
`MapEnvironment` and `@TempDir`, and call the package-private `CacheManager.resetDefaultInstance()` in
`@AfterEach` to flush and discard it (`CacheTest`, `CacheManagerTest`). Keep that pattern when adding tests
so suites stay order-independent.

## Backlog

None. The final page set covers all subsystems, operations, and testing.
