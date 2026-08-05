---
type: Architecture
title: Cache Backends
description: The LocalCache, RedisCache, and RestRedisCache backends, and the UnifiedRedisClient abstraction over Jedis and the REST-Redis HTTP client.
tags: [cache, backends, integration]
openwiki:
  roles: [architecture, integration, operations]
  change_kinds: [public-api, lifecycle]
  source_paths:
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/LocalCache.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RedisCache.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisCache.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/UnifiedRedisClient.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RedisAdapter.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisAdapter.java
  symbols: [LocalCache, RedisCache, RestRedisCache, UnifiedRedisClient, RedisAdapter, RestRedisAdapter]
  test_paths:
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
  invariants:
    - LocalCache auto-flushes after more than MAX_DIRTY (50) modifications; flush is atomic via a temp file.
    - RedisCache and RestRedisCache fail fast: if PING fails at construction, they throw IllegalStateException and close the client.
    - Redis entries are hashes keyed by cacheKey.toJsonKey() with fields data and timestamp.
    - RestRedisCache extends RedisCache and only overrides connection setup.
  validation_commands: ["mvn -q test -Dtest=CacheTest"]
---

# Cache Backends

Three backends implement `Cache<K>` (see [Cache Core](cache-core.md)). `CacheManager`
(see [Cache Hierarchy and Manager](cache-hierarchy.md)) stacks them; this page covers their
storage and operational contracts.

## LocalCache

`LocalCache<K>` (package-private) is a file-backed `Map<String, String>` persisted as JSON.

- **File:** `<cacheDir>/<name>.json`, where `name` is `<Origin>_<parameters>` with colons
  replaced by `__`. The format is compatible with LiSSA's existing caches (verified by
  `CacheTest.testBackwardCompatibility` against `src/test/resources/cache/test-local-cache-sample.json`).
- **Within a file:** content is mapped to values via the key's `localKey()` (a content UUID
  from [KeyGenerator](configuration.md)), so identical content always maps to the same entry.
- **Loading:** on construction, an existing non-empty file is read into memory; an empty file
  is deleted to ensure a clean state.
- **Writes:** atomic — writes to `<file>.tmp.json`, copies over the target, deletes the temp.
- **Auto-flush:** after more than `MAX_DIRTY = 50` modifications, `write()` runs automatically.
  `flush()` is an explicit `write()`.
- All mutating/reading methods are `synchronized`.

Because the files are self-contained, the cache directory is the replication artifact — see
[Operations and Deployment](operations.md).

## RedisCache and UnifiedRedisClient

`RedisCache<K>` (package-private) stores each entry as a Redis hash:

- **Hash key:** `cacheKey.toJsonKey()` (the JSON form of the key — see [Cache Keys](cache-keys.md)).
- **Fields:** `data` (the stored value, serialized to JSON) and `timestamp`
  (`Instant.now().getEpochSecond()`).
- `get` reads the `data` field and deserializes; `containsKey` uses Redis `EXISTS`; `flush`
  is a no-op (Redis persists immediately).

`RedisCache` does not talk to Redis directly. It depends on `UnifiedRedisClient`, a small
`AutoCloseable` interface exposing only the operations the cache uses: `ping`, `exists`,
`hget`, `hset`, `close`.

**Fail-fast:** `RedisCache.createRedisConnection()` reads `REDIS_URL` (default
`redis://localhost:6379`), builds a `RedisAdapter` over `redis.clients.jedis.RedisClient`,
and pings. If `PING` fails it closes the client and throws `IllegalStateException` ("Could not
connect to Redis ..."). There is no silent fallback — pair a remote backend with `LOCAL`
(see [Cache Hierarchy and Manager](cache-hierarchy.md)) if you want a local fallback layer.

`RedisAdapter` (package-private) adapts `UnifiedJedis` to `UnifiedRedisClient`.

## RestRedisCache

`RestRedisCache<K>` (package-private) extends `RedisCache` and overrides only connection
setup. It is for clients that can reach Redis only over HTTP (firewall / HTTP-only egress),
using `org.fuchss:rest-redis`'s `Client`.

- Reads `REST_REDIS_URI` (default `http://localhost:8080`), `REST_REDIS_USERNAME`, and
  `REST_REDIS_PASSWORD` (optional; blank treated as null and not sent). The server itself has
  no auth/TLS — terminate them at a reverse proxy (see [Operations and Deployment](operations.md)).
- Wraps the `Client` in a `RestRedisAdapter` (package-private) that delegates `ping`,
  `exists`, `hget`, `hset`, `close` to the REST client.
- Same fail-fast contract: if `PING` fails, it closes the client and throws
  `IllegalStateException` ("Could not connect to Redis at <uri>").

All storage behavior (hash key, fields, `data`/`timestamp`) is inherited from `RedisCache`.

## Adding a backend

1. Add a `CacheType` constant and a `case` in `Cache.createByType` (construct the backend from
   `CacheParameter` and, where needed, `cacheDir`/`mapper`).
2. Implement `Cache<K>` (or extend an existing backend like `RestRedisCache` extends
   `RedisCache`).
3. If it needs connection details, read them through [Environment](configuration.md) and
   preserve the fail-fast PING contract so layering and conflict strategies remain safe.
4. Add tests mirroring `RestRedisTest` (Testcontainers where a real server is needed; the
   `@Testcontainers(disabledWithoutDocker = true)` annotation skips gracefully without Docker).

## Focused tests

- `CacheTest` — local backend round-trips and legacy-file backward compatibility (see
  [Cache Core](cache-core.md)).
- `RestRedisTest` — Testcontainers-managed Redis plus an in-process `rest-redis` `Server`:
  connection, set/get/null, and `HierarchicalCache` conflict behavior (NONE returns primary
  and leaves secondary; OVERWRITE overwrites secondary; ERROR throws) across a local + REST-Redis
  pair. `@Testcontainers(disabledWithoutDocker = true)` skips the whole class without Docker.

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
classDiagram
    class UnifiedRedisClient {
        <<interface>>
        +ping() boolean
        +exists(String) boolean
        +hget(String, String) String
        +hset(String, String, String) long
        +close()
    }
    class RedisAdapter {
        -jedis UnifiedJedis
    }
    class RestRedisAdapter {
        -restRedisClient Client
    }
    class RedisCache~K~ {
        -redis UnifiedRedisClient
        +get(String, Class) T
        +put(String, String)
    }
    class RestRedisCache~K~ {
        +createRedisConnection() UnifiedRedisClient
    }
    class LocalCache~K~ {
        -cacheFile File
        -cache Map
        +write()
        +flush()
    }
    UnifiedRedisClient <|.. RedisAdapter
    UnifiedRedisClient <|.. RestRedisAdapter
    RedisCache~K~ --> UnifiedRedisClient : uses
    RestRedisCache~K~ --|> RedisCache~K~
```
*Cache backends: RedisCache is driven by UnifiedRedisClient; RestRedisCache extends it with an HTTP connection; LocalCache is independent.*
