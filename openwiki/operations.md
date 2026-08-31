---
type: Operations
title: Operations and Deployment
description: Deploying the LOCAL, Redis, and REST-Redis cache backends, building replication packages, the fail-fast connection contract, the CI/release workflows that build and publish the library, and the agent-guidance contract in AGENTS.md.
tags: [operations, deployment, ci, agent-guidance]
openwiki:
  roles: [operations, delivery]
  change_kinds: [lifecycle]
  source_paths:
    - README.md
    - pom.xml
    - sample.env
    - AGENTS.md
    - CLAUDE.md
    - .github/workflows/verify.yml
    - .github/workflows/format.yml
    - .github/workflows/openwiki.yml
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
  symbols: [central-publishing-maven-plugin, flatten-maven-plugin]
  invariants:
    - Redis and REST-Redis fail fast if PING fails at start-up; there is no silent fallback.
    - LOCAL cache files are self-contained and are the replication artifact.
    - Testcontainers REST-Redis integration tests skip automatically without Docker.
  validation_commands: ["mvn verify", "mvn -q test -Dtest=RestRedisTest"]
verified:
  - by: openwiki/0.4.3
    at: 2026-08-31T12:41:02.883Z
sources:
  - id: openwiki-source-1307a98427393d045f958ba3
    resource: repo://.github/CODEOWNERS
  - id: openwiki-source-79b37831c9c81206da1d88ec
    resource: repo://.github/dependabot.yml
  - id: openwiki-source-f721914ec66a43bd2b76a715
    resource: repo://.github/workflows/format.yml
  - id: openwiki-source-6d3ac2bdfb0e76882a670989
    resource: repo://.github/workflows/openwiki.yml
  - id: openwiki-source-4320a40cc58d6de85374b359
    resource: repo://.github/workflows/verify.yml
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-2355f81d7cf522f8dbdaabd4
    resource: repo://pom.xml
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-05268939a714aa11095abad9
    resource: repo://sample.env
  - id: openwiki-source-4fd2e823addcf84d995a9216
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/CacheManager.java
  - id: openwiki-source-9254ad363ba331094d066d08
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/HierarchicalCache.java
  - id: openwiki-source-9c0be2a8e438a2a14ff8210b
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RedisCache.java
  - id: openwiki-source-1b5f9de2685a5c30b15a20e4
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisAdapter.java
  - id: openwiki-source-406400bb0fa6384c6e01eaf2
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisCache.java
  - id: openwiki-source-40c4513abd4e4ec4b3783b4a
    resource: repo://src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/UnifiedRedisClient.java
  - id: openwiki-source-77fcb76d829af13337d04708
    resource: repo://src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/RestRedisTest.java
generated: { by: "openwiki/0.4.3", at: "2026-08-31T12:41:02.883Z" }
---

# Operations and Deployment

Operational guidance for the cache backends and the CI/release pipelines. Backend internals
are in [Cache Backends](cache-backends.md); this page covers how to run and deploy them, how
the repository builds and publishes, and how the generated wiki stays fresh under the
agent-guidance contract in `AGENTS.md`.

## Choosing a backend

- **Local / developing / shipping a replication package** → keep the default `CACHE_HIERARCHY=LOCAL`.
- **Sharing a cache across machines on a trusted network (and/or building a replication
  package while running against a shared cache)** → `REDIS,LOCAL`.
- **Clients can only talk HTTP, or you need auth/TLS in front of the cache** →
  `REST_REDIS,LOCAL`.

Layering is primary-first; writes are write-through to all layers, and reads backfill any
layer that is missing a value present in another (see
[Cache Hierarchy and Manager](cache-hierarchy.md)). `sample.env` is the template for all of
these variables (`CACHE_HIERARCHY`, `CACHE_REPLACEMENT_STRATEGY`, `REDIS_URL`,
`REST_REDIS_URI`, `REST_REDIS_USERNAME`, `REST_REDIS_PASSWORD`). Credentials and hosts are
read through `Environment` (`.env` from the working directory, falling back to system
environment variables); the cache directory itself is set in code via
`CacheManager.setCacheDir(...)`, not through an environment variable. All cache configuration
is read when the `CacheManager` is constructed, so changing it means constructing a new
manager.

## LOCAL

Nothing to deploy. `CACHE_HIERARCHY=LOCAL` writes self-contained JSON files into the
directory passed to `CacheManager.setCacheDir(...)`. These files are the replication artifact:
ship the directory and replicators can re-run offline with `CACHE_HIERARCHY=LOCAL`. Note the
lifecycle requirement: `CacheManager.getDefaultInstance()` throws until `setCacheDir` has been
called (pass `null` to use the default `"cache"` directory).

## Redis (`REDIS`)

Deploy a Redis server (e.g. `redis:7.4` with `--appendonly yes` for persistence) and point
clients at it:

```env
CACHE_HIERARCHY=REDIS,LOCAL
REDIS_URL=redis://redis-host:6379
```

Redis has no auth by default; if reachable beyond a trusted network, enable a password /
TLS and put credentials in the URL (`rediss://user:pass@host:6380`). `RedisCache` connects
via Jedis (wrapped in a `RedisAdapter` behind the `UnifiedRedisClient` interface) and **fails
fast** if `PING` fails at start-up — it closes the client and throws
`IllegalStateException` ("Could not connect to Redis. Make sure the container is up and
running."). There is no silent fallback, so pair it with `LOCAL` for a local fallback layer.

## REST-Redis (`REST_REDIS`)

For HTTP-only egress or auth/TLS in front of the cache. Topology:

```mermaid
flowchart LR
    A["client (this library)"] -->|"HTTP, Basic auth when set"| B["reverse proxy: TLS + auth"]
    B -->|"HTTP"| C["REST-Redis server (ghcr.io/dfuchss/rest-redis)"]
    C -->|"TCP"| D["Redis (appendonly yes)"]
```

*REST-Redis deployment topology: TLS and Basic auth terminate at the reverse proxy; the
REST-Redis server only proxies the few operations the cache uses (`ping`, `exists`, `hget`,
`hset`) to Redis.*

The REST-Redis server is `org.fuchss:rest-redis` (Docker image
`ghcr.io/dfuchss/rest-redis`); the matching client is bundled in this library, so applications
only need `REST_REDIS_URI`.

1. Write `server_config.json` (`redis_host`, `redis_port`, `http_port`).
2. Run Redis + the REST-Redis server with Docker Compose (see `README.md` for the full YAML).
3. Point clients at it:

```env
CACHE_HIERARCHY=REST_REDIS,LOCAL
REST_REDIS_URI=http://rest-redis-host:8080
# REST_REDIS_USERNAME / REST_REDIS_PASSWORD — only when a proxy enforces Basic auth
```

The server has no built-in auth/TLS; terminate them at a reverse proxy. `RestRedisCache`
treats blank `REST_REDIS_USERNAME`/`REST_REDIS_PASSWORD` as unset, so the client sends
Basic auth only when configured. It extends `RedisCache` and overrides only connection setup
(wrapping the `org.fuchss.restredis.client.Client` in a `RestRedisAdapter` behind
`UnifiedRedisClient`), so it inherits the same fail-fast `PING` contract: on a failed PING it
closes the client and throws `IllegalStateException` ("Could not connect to Redis at
\<uri\>"). All storage behavior (hash keyed by the JSON cache key with `data` and `timestamp`
fields) is inherited.

## Replication packages

Because `LOCAL` files are self-contained, the cache directory *is* the replication artifact.
To fill it while running experiments against a shared Redis, layer `LOCAL` underneath it
(`CACHE_HIERARCHY=REDIS,LOCAL`): every response is write-through to both, and entries already
in Redis are backfilled into the local files on first read. After the run, call
`CacheManager.getDefaultInstance().flush()` to ensure everything is on disk, then ship that
directory. Replicators unpack it, set `CACHE_HIERARCHY=LOCAL`, and re-run offline — no API
keys, Redis, or model access required.

## Build and release

- **Build / verify:** `mvn verify`. The Testcontainers-based `RestRedisTest` (see
  [Cache Backends](cache-backends.md)) is skipped automatically when Docker is unavailable
  (`@Testcontainers(disabledWithoutDocker = true)`); with Docker available,
  `mvn -q test -Dtest=RestRedisTest` runs just that integration test. It spins up a
  `redis:latest` container, starts the `org.fuchss.restredis.server.Server` in-process against
  it (configured through a generated `server_config.json` with a free HTTP port), waits for
  readiness, and exercises connection, set/get, and the `HierarchicalCache`
  NONE/OVERWRITE/ERROR/backfill behavior across a local + REST-Redis pair.
- **Publishing:** `pom.xml` configures `central-publishing-maven-plugin` with
  `publishingServerId=central`, `autoPublish=true`, `waitUntil=published`, and
  `deploymentName=ardoco-llm-access` (overriding the parent's default `ardoco` so this
  library's bundle is named distinctly in the Central Publisher Portal). The
  `flatten-maven-plugin` version is pinned to `1.7.3` because the ARDoCo parent references it
  without a version — Maven would resolve the latest (1.8.0), which has an NPE regression in
  its CI-friendly interpolator (see the comment in `pom.xml`).

## CI workflows (`.github/workflows/`)

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `verify.yml` | push to `main` (excluding `v*` tags), PR (`opened`/`synchronize`/`reopened`), manual — `docs/**` and `openwiki/**` paths ignored in both push and PR | Reusable Maven build/verify via `ardoco/actions/.github/workflows/maven.yml@main` with `deploy: false` and `with-submodules: true`; `CENTRAL_USER`, `CENTRAL_TOKEN`, and `GPG_KEY` are passed as empty strings, so CI builds and tests but never publishes. |
| `format.yml` | PR to `main`, manual | Checks out with submodules and full history, derives the JDK version from the repository config (`joshlong/java-version-export-github-action`), sets up Temurin with a Maven cache, runs `mvn -B spotless:apply --file pom.xml`, and auto-commits the result ("Apply formatting changes"). |
| `openwiki.yml` | weekly (`0 6 * * 1`, Monday 06:00 UTC), manual | Grants `contents: write` and `pull-requests: write` and delegates to the shared `ardoco/actions/.github/workflows/openwiki.yml@main` reusable workflow, forwarding only `OPENROUTER_API_KEY`. |

This repository's `openwiki.yml` only wires the reusable workflow and the `OPENROUTER_API_KEY`
secret; the commit-gating, `openwiki --update --print` invocation, and update-PR creation live
in the shared `ardoco/actions` workflow, not inlined here. Generated wiki content lives under
`/openwiki`.

Two further automation pieces feed these pipelines: Dependabot opens weekly dependency-update
PRs for both the `github-actions` and `maven` ecosystems (Wednesdays 11:00 Europe/Berlin),
which are then validated by `verify.yml`; and `CODEOWNERS` routes all files to
`@ardoco/maintainer` for review.

## Agent guidance and wiki freshness

`AGENTS.md` is the single source of agent guidance for this repository; `CLAUDE.md` is only a
pointer to it. The contract it defines:

- The generated `openwiki/` evidence index is **optional just-in-time context, not required
  startup reading**.
- **Source code and tests are authoritative.** A brief's unknowns and review items are
  verification gaps, not automatic requirements.
- **Prefer the narrowest quiet validation** that proves the changed behavior, and preserve
  complete failure output.
- **Do not hand-edit generated OpenWiki pages** unless explicitly asked — change source code
  or docs and let the scheduled OpenWiki workflow regenerate them.

Regeneration is driven by `openwiki.yml` (weekly Monday 06:00 UTC or manual dispatch): it
delegates to the shared `ardoco/actions` workflow, which performs the update run, gates on
recent commits to avoid churn, and opens the update PR. Because commits touching only
`openwiki/**` and `docs/**` are excluded from `verify.yml`, wiki refreshes do not trigger
Maven builds.
