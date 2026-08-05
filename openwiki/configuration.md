---
type: Reference
title: Configuration and Environment
description: Environment loads .env over system env vars and is the single source of credentials and hosts; KeyGenerator produces stable content UUIDs for cache keys; Futures resolves parallel embedding futures.
tags: [configuration, environment, utilities]
openwiki:
  roles: [repository, integration]
  change_kinds: [public-api]
  source_paths:
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/Environment.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/KeyGenerator.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/util/Futures.java
    - sample.env
  symbols: [Environment, Environment.getenv, Environment.getenvNonNull, Environment.overwrite, KeyGenerator, KeyGenerator.generateKey, Futures, Futures.getLogged]
  test_paths:
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/EnvironmentTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/KeyGeneratorTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/util/FuturesTest.java
  invariants:
    - Precedence is .env values then system env vars then null.
    - Environment.overwrite replaces the Dotenv config; a missing path keeps the previous config.
    - KeyGenerator normalizes CRLF to LF before hashing and produces type-3 name UUIDs.
    - Futures.getLogged never throws a checked exception; failures become IllegalStateException.
  validation_commands: ["mvn -q test -Dtest=EnvironmentTest,KeyGeneratorTest,FuturesTest"]
---

# Configuration and Environment

The library is framework-neutral: model settings come from configuration objects
([Chat Models](chat-models.md), [Embeddings](embeddings.md)), while credentials and hosts
come from the environment. `Environment` is the single accessor; `KeyGenerator` and `Futures`
are small utilities used by the cache and embedding subsystems.

## Environment

`edu.kit.kastel.mcse.ardoco.llm.util.Environment` is a final utility class backed by
`io.github.cdimascio.dotenv` (dotenv-java). On class load it tries to load a `.env` from the
project root; otherwise it falls back to system environment variables.

- `getenv(String key)` — `.env` value first, then `System.getenv`, then `null`.
- `getenvNonNull(String key)` — throws `IllegalStateException` naming the missing key.
  Used by creators/providers that require a value (e.g. `OLLAMA_EMBEDDING_HOST`).
- `overwrite(Path path)` — `synchronized`; replaces the Dotenv configuration from a custom
  path. If the file does not exist, it logs a warning and keeps the existing config (used by
  tests to point at `src/test/resources/.env-test`).

`dotenv` is `volatile` and `load`/`overwrite` are `synchronized`, so publication is safe.
**Do not read `.env` files directly;** use `Environment` or `getenv`/`getenvNonNull`.

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

- `EnvironmentTest` — `readsFromDotEnv`, `fallsBackToSystemEnv` (reads `PATH`),
  `unknownReturnsNull`, `nonNullThrows`, `overwriteMissingKeepsPrevious`. Uses a `@TempDir`
  `.env` and restores the baseline `.env-test` in `@AfterEach`.
- `KeyGeneratorTest` — `deterministic`, `normalizesLineEndings`, `distinctInputs`,
  `stableUuidValue`, `nullInput`.
- `FuturesTest` — `returnsValue` (completed future), `wrapsFailure` (failed future throws
  `IllegalStateException`).

## Test environment

Tests call `Environment.overwrite(Path.of("src/test/resources/.env-test"))` (in `@BeforeAll`
or `@BeforeEach`) to load deterministic dummy values. The `.env-test` file provides
`OPENAI_API_KEY=DUMMY`, `OPENAI_ORGANIZATION_ID=DUMMY`, `OLLAMA_HOST`/
`OLLAMA_EMBEDDING_HOST` set to `http://localhost:11434`, and `CACHE_HIERARCHY=LOCAL` with
`CACHE_REPLACEMENT_STRATEGY=ERROR`. Do not commit real secrets to `.env` or `.env-test`.
