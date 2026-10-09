---
type: Reference
title: Configuration and Environment
description: An injected EnvironmentProvider (SystemEnvironment for system env vars plus .env, MapEnvironment for in-memory values) is the source of credentials and hosts for chat, embedding, and cache; KeyGenerator produces stable content UUIDs for cache keys; Futures resolves parallel embedding futures.
tags: [configuration, environment, utilities]
openwiki:
  roles: [repository, integration]
  change_kinds: [public-api]
  source_paths:
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/EnvironmentProvider.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/SystemEnvironment.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/MapEnvironment.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/KeyGenerator.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/Futures.java
    - sample.env
  symbols: [EnvironmentProvider, EnvironmentProvider.getenv, EnvironmentProvider.getenvNonNull, SystemEnvironment, MapEnvironment, KeyGenerator, KeyGenerator.generateKey, Futures, Futures.getLogged]
  test_paths:
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/SystemEnvironmentTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/MapEnvironmentTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/NoGlobalEnvironmentTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/KeyGeneratorTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/FuturesTest.java
  invariants:
    - There is no global environment; an EnvironmentProvider is only ever a constructor parameter or an instance field (guarded by NoGlobalEnvironmentTest).
    - SystemEnvironment precedence is system env vars, then .env values, then null.
    - MapEnvironment is an immutable copy of its map, never falls back to system env vars, and its toString lists keys only.
    - getenvNonNull throws IllegalStateException naming the missing key.
    - KeyGenerator normalizes CRLF to LF before hashing and produces type-3 name UUIDs.
    - Futures.getLogged never throws a checked exception; failures become IllegalStateException.
  validation_commands: ["mvn -q test -Dtest=SystemEnvironmentTest,MapEnvironmentTest,NoGlobalEnvironmentTest,KeyGeneratorTest,FuturesTest"]
---

# Configuration and Environment

The library is framework-neutral: model settings come from configuration objects
([Chat Models](chat-models.md), [Embeddings](embeddings.md)), while credentials and hosts
come from an `EnvironmentProvider` that is passed in explicitly. `KeyGenerator` and `Futures`
are small utilities used by the cache and embedding subsystems.

## EnvironmentProvider

`edu.kit.kastel.mcse.ardoco.llm.util.EnvironmentProvider` is the single abstraction for reading
configuration values. Where the values come from is implementation-specific.

- `getenv(String key)` — the value, or `null` if the key is not set.
- `getenvNonNull(String key)` — default method; throws `IllegalStateException` naming the
  missing key. Used by creators/providers that require a value (e.g. `OLLAMA_EMBEDDING_HOST`).

**There is no global environment.** An environment is always a constructor parameter or an
instance field — never a static field or a singleton. `NoGlobalEnvironmentTest` (ArchUnit)
fails the build if a static field in main code is assignable to `EnvironmentProvider`.
**Do not read `.env` files or `System.getenv` directly;** use the injected provider.

### Implementations

- `SystemEnvironment` — backed by `io.github.cdimascio.dotenv` (dotenv-java).
  `new SystemEnvironment()` loads `.env` from the current working directory if it exists;
  `new SystemEnvironment(Path)` loads the given file (a missing file logs a warning and falls back
  to system env vars). The file is loaded once in the constructor; instances are immutable and
  independent. Precedence: **system env vars first, then `.env`, then `null`** — dotenv-java
  resolves `System.getenv(key)` before the parsed file, so a `.env` entry is used only when the
  variable is not exported.
- `MapEnvironment` — a `final` class over an immutable `Map.copyOf` of the given map. Unknown keys
  return `null`; there is no fallback to system env vars. `toString()` lists the keys only, so
  secret values do not end up in logs.

### Where the environment is injected

| Consumer | How to pass it | Default when omitted |
| --- | --- | --- |
| Chat ([Chat Models](chat-models.md)) | `LlmConfiguration.builder(platform).environment(env)` → `ChatModelProvider` | `new SystemEnvironment()` per `build()` |
| Embeddings ([Embeddings](embeddings.md)) | `EmbeddingConfiguration.builder(platform).environment(env)` → `EmbeddingCreator.create` → creator constructors | `new SystemEnvironment()` per `build()` |
| Cache ([Cache Hierarchy and Manager](cache-hierarchy.md)) | `CacheManager.setCacheDir(dir, env)` or `new CacheManager(path, env)` → `Cache.createByType` → `RedisCache`/`RestRedisCache` | `new SystemEnvironment()` in `setCacheDir(dir)` / `new CacheManager(path)` |

Usage:

```java
EnvironmentProvider environment = new MapEnvironment(Map.of("OPENAI_API_KEY", apiKey));

LlmConfiguration configuration = LlmConfiguration.builder(ChatModelPlatform.OPENAI)
        .modelName("gpt-4o-mini")
        .environment(environment)
        .build();
ChatModel model = new ChatModelProvider(configuration).createChatModel();
```

### Environment variables

See [`sample.env`](../sample.env) for a template. Chat and embedding env vars are documented
in [Chat Models](chat-models.md) and [Embeddings](embeddings.md); cache env vars in
[Cache Hierarchy and Manager](cache-hierarchy.md) and [Cache Backends](cache-backends.md):

| Variable | Scope | Default | Purpose |
| --- | --- | --- | --- |
| `CACHE_HIERARCHY` | cache | `LOCAL` | Comma-separated backends, primary first |
| `CACHE_REPLACEMENT_STRATEGY` | layered cache | `NONE` | `NONE`/`ERROR`/`OVERWRITE` |
| `REDIS_URL` | `REDIS` | `redis://localhost:6379` | Redis TCP connection URL |
| `REST_REDIS_URI` | `REST_REDIS` | `http://localhost:8080` | REST-Redis HTTP base URL |
| `REST_REDIS_USERNAME` / `REST_REDIS_PASSWORD` | `REST_REDIS` | — | HTTP Basic auth (only when set) |

The cache directory itself is set in code via `CacheManager.setCacheDir(...)`, not an env
var (see [Cache Hierarchy and Manager](cache-hierarchy.md)).

## KeyGenerator

`KeyGenerator.generateKey(String input)` produces a deterministic type-3 (name-based) UUID
via `UUID.nameUUIDFromBytes` over UTF-8 bytes. It normalizes `\r\n` → `\n` before hashing, so
platform line-ending differences do not split cache entries.

Invariants:

- Throws `IllegalArgumentException` for null input.
- Stable format: `generateKey("test") == "098f6bcd-4621-3373-8ade-4e832627b4f6"` (guarded by
  `KeyGeneratorTest.stableUuidValue` for backward compatibility).

Used by [Cache Keys](cache-keys.md) to compute `localKey`, and by `CachedEmbeddingCreator`
for logging content hashes.

## Futures

`Futures.getLogged(Future<T>, Logger)` resolves a future and never rethrows a checked
exception: `InterruptedException` restores the interrupt flag and throws
`IllegalStateException`; any other exception is logged and wrapped in `IllegalStateException`.
Used by the parallel path of `CachedEmbeddingCreator` (see [Embeddings](embeddings.md)).

## Focused tests

- `SystemEnvironmentTest` — `readsFromDotEnv`, `fallsBackToSystemEnv` (reads `PATH`),
  `unknownReturnsNull`, `nonNull`, `missingFileUsesSystemEnvironment`, `instancesAreIndependent`,
  `equalityAndToString`. Each test writes its own `.env` into a `@TempDir`.
- `MapEnvironmentTest` — `lookup`, `noSystemFallback`, `nonNull`, `defensiveCopy`,
  `toStringHidesValues`, `equality`.
- `NoGlobalEnvironmentTest` — ArchUnit rules: no static field in main code is assignable to
  `EnvironmentProvider`, and `SystemEnvironment` declares no static field of its own type.
- `KeyGeneratorTest` — `deterministic`, `normalizesLineEndings`, `distinctInputs`,
  `stableUuidValue`, `nullInput`.
- `FuturesTest` — `returnsValue` (completed future), `wrapsFailure` (failed future throws
  `IllegalStateException`).

## Test environment

Tests never depend on the developer's `.env` or system variables: they inject a
`MapEnvironment` with deterministic dummy values, e.g.
`CacheManager.setCacheDir(dir, new MapEnvironment(Map.of("CACHE_HIERARCHY", "LOCAL", "CACHE_REPLACEMENT_STRATEGY", "ERROR")))`
or `LlmConfiguration.builder(...).environment(new MapEnvironment(Map.of("OPENAI_API_KEY", "DUMMY")))`.
An empty `MapEnvironment` makes missing-credential tests deterministic. Do not commit real
secrets to `.env`.
