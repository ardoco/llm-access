---
type: Operations
title: Operations and Deployment
description: Deploying the LOCAL, Redis, and REST-Redis cache backends, the replication-package workflow, the fail-fast connection contract, and the CI/release pipelines that build, format, and publish the library.
tags: [operations, deployment, ci, caching, redis]
openwiki:
  roles: [operations, delivery]
  change_kinds: [lifecycle]
  source_paths:
    - .github/workflows/format.yml
    - .github/workflows/openwiki.yml
    - .github/workflows/verify.yml
    - README.md
    - pom.xml
    - sample.env
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManager.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RedisCache.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisCache.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
  symbols: [CacheManager, CacheManager.setCacheDir, CacheManager.getDefaultInstance, CacheManager.flush, RedisCache, RestRedisCache, UnifiedRedisClient, CacheType, central-publishing-maven-plugin, flatten-maven-plugin]
  invariants:
    - Redis and REST-Redis fail fast: an unreachable backend or a failed PING at cache construction aborts with an exception; there is no silent fallback.
    - LOCAL cache files are self-contained and are the replication artifact.
    - The Testcontainers REST-Redis integration test skips automatically without Docker.
  validation_commands: ["mvn verify", "mvn -q test -Dtest=RestRedisTest"]
verified:
  - by: openwiki/0.7.2
    at: 2026-10-13T13:30:47.057Z
sources:
  - id: openwiki-source-f721914ec66a43bd2b76a715
    resource: repo://.github/workflows/format.yml
  - id: openwiki-source-6d3ac2bdfb0e76882a670989
    resource: repo://.github/workflows/openwiki.yml
  - id: openwiki-source-4320a40cc58d6de85374b359
    resource: repo://.github/workflows/verify.yml
  - id: openwiki-source-2355f81d7cf522f8dbdaabd4
    resource: repo://pom.xml
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-4fd2e823addcf84d995a9216
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManager.java
  - id: openwiki-source-9a928fd3a5261773953a3859
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheReplacementStrategy.java
  - id: openwiki-source-9254ad363ba331094d066d08
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/HierarchicalCache.java
  - id: openwiki-source-1f5a15e9c371ebda0b1a11b5
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/LocalCache.java
  - id: openwiki-source-9c0be2a8e438a2a14ff8210b
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RedisCache.java
  - id: openwiki-source-1b5f9de2685a5c30b15a20e4
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisAdapter.java
  - id: openwiki-source-406400bb0fa6384c6e01eaf2
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisCache.java
  - id: openwiki-source-40c4513abd4e4ec4b3783b4a
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/UnifiedRedisClient.java
  - id: openwiki-source-30f99b58023b3a9a8c01b6d4
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManagerTest.java
  - id: openwiki-source-77fcb76d829af13337d04708
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
generated: { by: "openwiki/0.7.2", at: "2026-10-13T13:30:47.057Z" }
---

# Operations and Deployment

Operational guidance for the three cache backends and for the pipelines that build, format, and
publish the library. Storage internals are in [Cache Backends](../cache/cache-backends.md),
layering and conflict resolution in
[Cache Hierarchy and Manager](../cache/cache-hierarchy.md), and the environment seam behind
every setting on this page in
[Environment and Configuration](../concepts/environment-provider.md).

## Choosing a backend

| Backend | What you deploy | Use when |
| --- | --- | --- |
| `LOCAL` | nothing — JSON files under the cache directory | single-machine or developer runs (the default), replication packages |
| `REDIS` | a Redis server reachable on its TCP port | a cache shared across machines or runs on a trusted network |
| `REST_REDIS` | a Redis server **and** the REST-Redis HTTP server | clients can only talk HTTP (firewall / HTTP-only egress), or you want TLS/auth in front of the cache |

The backend stack is selected with `CACHE_HIERARCHY`, a comma-separated, case-insensitive list of
cache types ordered **primary first**; a single entry (the default `LOCAL`) means no layering.
The two recommended shared-cache setups layer the local files underneath the remote cache:

```env
CACHE_HIERARCHY=REDIS,LOCAL        # shared cache on a trusted network, local files underneath
CACHE_HIERARCHY=REST_REDIS,LOCAL   # same, but the remote cache is reached over HTTP
```

Writes are **write-through** to all layers, and reads consult all layers, backfilling any layer
that is missing a value another one holds — in either direction (see
[Cache Hierarchy and Manager](../cache/cache-hierarchy.md)). The first entry is the primary
layer: under the default `NONE` conflict strategy its value wins when two layers disagree.

## Deployment configuration

All cache behaviour is driven by environment variables read from the `CacheManager`'s
`EnvironmentProvider`; [`sample.env`](../../sample.env) is the template. (Chat and embedding
platform variables are listed on the
[Environment and Configuration](../concepts/environment-provider.md) page and are not repeated
here.)

| Variable | Applies to | Default | Purpose |
| --- | --- | --- | --- |
| `CACHE_HIERARCHY` | all | `LOCAL` | Comma-separated backends, primary first (`LOCAL`, `REDIS`, `REST_REDIS`) |
| `CACHE_REPLACEMENT_STRATEGY` | layered caches | `NONE` | Conflict handling between layers: `NONE`, `ERROR`, `OVERWRITE` |
| `REDIS_URL` | `REDIS` | `redis://localhost:6379` | Redis connection URL |
| `REST_REDIS_URI` | `REST_REDIS` | `http://localhost:8080` | Base URL of the REST-Redis server (or the proxy in front of it) |
| `REST_REDIS_USERNAME` / `REST_REDIS_PASSWORD` | `REST_REDIS` | — | HTTP Basic-auth credentials, sent only when set |

The cache **directory** is not an environment variable: it is set in code via
`CacheManager.setCacheDir(...)` (the directory is created if missing; `null` selects the default
`cache` directory), and `CacheManager.getDefaultInstance()` throws until it has been called.
Alternatively, construct `new CacheManager(path, environment)` and pass the manager on
explicitly.

**When values are read.** The hierarchy and the replacement strategy are read once at
`CacheManager` construction. The connection settings (`REDIS_URL`, `REST_REDIS_URI`, …) are read
when a cache is first requested via `getCache`, which is also when the Redis-based backends
connect — a manager configured for `REDIS` performs no network I/O until that first call.

## LOCAL

Nothing to deploy: `CACHE_HIERARCHY=LOCAL` writes self-contained JSON files into the directory
passed to `CacheManager.setCacheDir(...)`, one file per caller and model configuration. Because
the files are self-contained, this directory is the format used for **replication packages**
(see below).

Note the persistence timing: the local layer keeps entries in memory and persists atomically on
`flush()`, auto-flushing only once more than 50 modifications have been made. Always end a run
with an explicit `CacheManager.flush()` before relying on the directory's contents.

## Deploying Redis

Use `REDIS` when several machines or runs should share one cache over a trusted network.
`docker-compose.yml`:

```yaml
services:
  redis:
    image: redis:7.4
    command: ["redis-server", "--appendonly", "yes"] # persist to disk so the cache survives restarts
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

volumes:
  redis-data:
```

Point clients at it:

```env
CACHE_HIERARCHY=REDIS,LOCAL
REDIS_URL=redis://redis-host:6379
```

Redis has no authentication by default. If it is reachable beyond a trusted network, enable a
password (`--requirepass`) and/or TLS and put the credentials into the URL, e.g.
`REDIS_URL=rediss://user:pass@host:6380`.

## Deploying REST-Redis

REST-Redis exists for the case where clients can reach the cache only over **HTTP** — restricted
egress, or a shared team cache whose TLS and authentication are terminated at a proxy. The
server is [`org.fuchss:rest-redis`](https://central.sonatype.com/artifact/org.fuchss/rest-redis)
(bundled here at version 0.1.5), published as the Docker image
[`ghcr.io/dfuchss/rest-redis`](https://github.com/dfuchss/rest-redis); the matching client ships
with this library, so applications only configure `REST_REDIS_URI`.

```mermaid
flowchart LR
    CLIENT["client (this library)"] -->|"HTTP"| PROXY["reverse proxy: TLS and Basic auth"]
    PROXY -->|"HTTP"| SERVER["REST-Redis server (ghcr.io/dfuchss/rest-redis)"]
    SERVER -->|"TCP"| REDIS["Redis (redis:7.4)"]
```

*Deployment topology. The reverse proxy is needed only when the server is exposed beyond a
trusted network; on a trusted network the client can point `REST_REDIS_URI` at the server
directly.*

1. **Write the server config** as `server_config.json`. It points the server at your Redis and
   picks the HTTP port to serve on:

   ```json
   {
     "redis_host": "redis",
     "redis_port": 6379,
     "http_port": 8080
   }
   ```

2. **Run Redis and the REST-Redis server** together with Docker Compose — or run only the server
   against an existing Redis:
   `docker run -p 8080:8080 -v "$(pwd)/server_config.json:/app/server_config.json:ro" ghcr.io/dfuchss/rest-redis`

   ```yaml
   services:
     redis:
       image: redis:7.4
       command: ["redis-server", "--appendonly", "yes"] # persist so the cache survives restarts
       volumes:
         - redis-data:/data

     rest-redis:
       image: ghcr.io/dfuchss/rest-redis
       depends_on:
         - redis
       volumes:
         - ./server_config.json:/app/server_config.json:ro
       ports:
         - "8080:8080" # expose directly only on a trusted network — otherwise front it with a proxy

   volumes:
     redis-data:
   ```

3. **Point clients at it:**

   ```env
   CACHE_HIERARCHY=REST_REDIS,LOCAL
   REST_REDIS_URI=http://rest-redis-host:8080
   # REST_REDIS_USERNAME / REST_REDIS_PASSWORD — only when a proxy in front enforces Basic auth
   ```

The server itself has **no built-in authentication or TLS**. When exposing it beyond a trusted
network, put a reverse proxy (nginx, Caddy, Traefik, …) in front to terminate TLS and Basic
auth, point `REST_REDIS_URI` at the proxy, and set `REST_REDIS_USERNAME` / `REST_REDIS_PASSWORD`
— the client sends them only when set (blank values count as unset).

A thin HTTP server is enough because the cache uses only four Redis operations: the
`UnifiedRedisClient` seam exposes `ping`, `exists`, `hget`, and `hset` (plus `close`), and both
the Jedis-backed `RedisAdapter` and the REST-backed `RestRedisAdapter` implement it.
`RestRedisCache` extends `RedisCache` and overrides only the connection setup, so the storage
layout behind the server — Redis hashes keyed by the JSON form of the cache key, with `data` and
`timestamp` fields — is identical for both transports (see
[Cache Backends](../cache/cache-backends.md)).

## The fail-fast connection contract

Both Redis-based backends connect and `PING` when the cache is constructed — there is **no
silent fallback** to another backend:

- `RedisCache` reads `REDIS_URL` (unset or blank falls back to `redis://localhost:6379`) and
  pings through the Jedis-backed adapter. A server that cannot be reached surfaces as the Jedis
  connection exception; a reachable server whose `PING` fails causes the client to be closed and
  `IllegalStateException` ("Could not connect to Redis. Make sure the container is up and
  running.") to be thrown.
- `RestRedisCache` reads `REST_REDIS_URI` (default `http://localhost:8080`) and applies the same
  contract: a failed `PING` closes the client and throws `IllegalStateException`
  ("Could not connect to Redis at \<uri\>").

```mermaid
sequenceDiagram
    participant App as Application
    participant Mgr as CacheManager
    participant B as Redis-based cache
    participant S as Redis or REST-Redis server
    App->>Mgr: getCache
    Mgr->>B: construct and connect
    B->>S: connect then PING
    alt reachable and answers PONG
        S-->>B: PONG
        B-->>Mgr: connected cache
    else unreachable
        B-->>App: connection exception from the client
    else PING fails
        B->>B: close client
        B-->>App: IllegalStateException
    end
```

*Cache acquisition: construction connects and pings; failures abort with an exception instead of
falling back.*

Because a failed connection aborts cache creation, pair a remote backend with `LOCAL`
(`REDIS,LOCAL` / `REST_REDIS,LOCAL`) when you want a local fallback layer. Note what that
fallback does and does not buy you: the backends of a hierarchy are created in order at the
first `getCache`, so a down Redis still fails cache acquisition even in a `REDIS,LOCAL` setup.
The `LOCAL` layer provides data fallback and offline reproduction — not startup tolerance.

## Replication packages

Because `LOCAL` cache files are self-contained, the cache directory *is* the replication
artifact: ship it and anyone can reproduce a run **offline** — no API keys, no Redis, no model
access — by pointing their `CacheManager` at it with `CACHE_HIERARCHY=LOCAL`.

```mermaid
flowchart TD
    A["Experiment with CACHE_HIERARCHY=REDIS,LOCAL or REST_REDIS,LOCAL"] --> B["Every response is written through to the remote layer and the local files"]
    B --> C["Entries already in the remote layer are backfilled into the local files on first read"]
    C --> D["End of run: CacheManager.flush persists the local layer"]
    D --> E["Ship the self-contained cache directory"]
    E --> F["Replicators set CACHE_HIERARCHY=LOCAL and re-run offline"]
    F --> G["No API keys, no Redis, no model access required"]
```

*The replication-package workflow: run against the shared cache, flush, ship the local
directory, and let replicators re-run offline.*

To fill the local cache while running experiments against a shared Redis, layer `LOCAL`
underneath it. With `REDIS,LOCAL` (or `REST_REDIS,LOCAL`) every response is write-through to
both layers, and any entry already in the remote layer — from an earlier run or a teammate — is
backfilled into the local files the first time this run reads it. After the experiment the local
directory holds every request the run touched.

End the run with `CacheManager.getDefaultInstance().flush()` (or `manager.flush()`, which
cascades through every layer of every cache the manager handed out). The flush matters only for
the `LOCAL` layer — the Redis-backed `flush` is a no-op because Redis persists immediately — and
it is necessary because the local layer otherwise auto-flushes only after more than 50
modifications. Then ship the directory; replicators unpack it, set `CACHE_HIERARCHY=LOCAL`, and
re-run.

## Build, test, and release

- **Build and test:** `mvn verify` is the full check. The Testcontainers-based `RestRedisTest`
  integration test is annotated `@Testcontainers(disabledWithoutDocker = true)`, so it is
  skipped automatically when no Docker environment is available; with Docker it starts a
  `redis:latest` container plus an in-JVM REST-Redis `Server` on a free port, injects the
  connection settings through a `MapEnvironment`, and exercises the deployed topology end to
  end, including the hierarchical conflict strategies (see
  [Testing Strategy](../testing/strategy.md)). Run it alone with
  `mvn -q test -Dtest=RestRedisTest` (Docker required).
- **Coordinates:** the artifact is `io.github.ardoco:llm-access` (currently `0.2.1-SNAPSHOT`),
  built with Java 21 on the `io.github.ardoco:parent` `2.1.0` parent POM.
- **Publishing:** `central-publishing-maven-plugin` is configured with `publishingServerId`
  `central`, `autoPublish=true`, `waitUntil=published`, and `deploymentName=ardoco-llm-access` —
  overriding the parent's default deployment name so the bundle is named distinctly in the
  Central Publisher Portal.
- **Pinned plugin:** `flatten-maven-plugin` is pinned to `1.7.3`. The ardoco parent references
  it without a version, so Maven would otherwise resolve the latest `1.8.0`, which has an NPE
  regression in its CI-friendly interpolator (see the comment in `pom.xml`).

## CI workflows

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `verify.yml` | push to `main` (ignoring `v*` tags), PRs (opened/synchronize/reopened), manual | Runs the shared ardoco Maven workflow (`ardoco/actions/.github/workflows/maven.yml@main`) with `deploy: false` and submodules checked out; `paths-ignore` for `docs/**` and `openwiki/**` keeps wiki- or doc-only changes from triggering builds. |
| `format.yml` | PRs to `main` (no path filter), manual | Derives the JDK version from the POM, runs `mvn -B spotless:apply`, and auto-commits the result as "Apply formatting changes". |
| `openwiki.yml` | weekly schedule (`0 6 * * 1`, Monday 06:00 UTC), manual | Delegates to the shared `ardoco/actions/.github/workflows/openwiki.yml@main` workflow with `contents: write` and `pull-requests: write`, forwarding only `OPENROUTER_API_KEY`. The update logic (commit gating, PR creation) lives in that shared workflow, not in this repository's file. |

Dependency upkeep is automated: Dependabot opens weekly update PRs for the `github-actions` and
`maven` ecosystems (Wednesdays 11:00 Europe/Berlin) with the commit-message prefix `Dependency`.

## Where to read next

- [Cache Hierarchy and Manager](../cache/cache-hierarchy.md) — layering, conflict strategies,
  and the manager lifecycle behind `CACHE_HIERARCHY`.
- [Cache Backends](../cache/cache-backends.md) — storage layout and the `UnifiedRedisClient`
  transport seam.
- [Environment and Configuration](../concepts/environment-provider.md) — how
  `SystemEnvironment`/`MapEnvironment` supply every variable above.
- [Testing Strategy](../testing/strategy.md) — hermetic tests and the Docker-gated integration
  test.
- [Architecture Overview](../architecture/overview.md) — where the cache subsystem sits and the
  toolchain/release summary.
