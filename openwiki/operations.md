---
type: Operations
title: Operations and Deployment
description: Deploying the LOCAL, Redis, and REST-Redis cache backends, building replication packages, the fail-fast connection contract, and the CI/release workflows that build and publish the library.
tags: [operations, deployment, ci]
openwiki:
  roles: [operations, delivery]
  change_kinds: [lifecycle]
  source_paths:
    - README.md
    - pom.xml
    - .github/workflows/verify.yml
    - .github/workflows/format.yml
    - .github/workflows/openwiki.yml
  symbols: [central-publishing-maven-plugin, flatten-maven-plugin]
  invariants:
    - Redis and REST-Redis fail fast if PING fails at start-up; there is no silent fallback.
    - LOCAL cache files are self-contained and are the replication artifact.
    - Testcontainers REST-Redis integration tests skip automatically without Docker.
  validation_commands: ["mvn verify", "mvn -q test -Dtest=RestRedisTest"]
---

# Operations and Deployment

Operational guidance for the cache backends and the CI/release pipelines. Backend internals
are in [Cache Backends](cache-backends.md); this page covers how to run and deploy them and
how the repository builds and publishes.

## Choosing a backend

- **Local / developing / shipping a replication package** → keep the default `CACHE_HIERARCHY=LOCAL`.
- **Sharing a cache across machines on a trusted network (and/or building a replication
  package while running against a shared cache)** → `REDIS,LOCAL`.
- **Clients can only talk HTTP, or you need auth/TLS in front of the cache** →
  `REST_REDIS,LOCAL`.

Layering is primary-first; writes are write-through to all layers, and reads backfill any
layer that is missing a value present in another (see
[Cache Hierarchy and Manager](cache-hierarchy.md)).

## LOCAL

Nothing to deploy. `CACHE_HIERARCHY=LOCAL` writes self-contained JSON files into the
directory passed to `CacheManager.setCacheDir(...)`. These files are the replication artifact:
ship the directory and replicators can re-run offline with `CACHE_HIERARCHY=LOCAL`.

## Redis (`REDIS`)

Deploy a Redis server (e.g. `redis:7.4` with `--appendonly yes` for persistence) and point
clients at it:

```env
CACHE_HIERARCHY=REDIS,LOCAL
REDIS_URL=redis://redis-host:6379
```

Redis has no auth by default; if reachable beyond a trusted network, enable a password /
TLS and put credentials in the URL (`rediss://user:pass@host:6380`). `RedisCache` connects
via Jedis and **fails fast** if `PING` fails at start-up — there is no silent fallback, so
pair it with `LOCAL` for a local fallback layer.

## REST-Redis (`REST_REDIS`)

For HTTP-only egress or auth/TLS in front of the cache. Topology:
`client (this library) --HTTP--> [reverse proxy: TLS + auth] --HTTP--> REST-Redis server --TCP--> Redis`.
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

The server has no built-in auth/TLS; terminate them at a reverse proxy. The client sends
`REST_REDIS_USERNAME`/`REST_REDIS_PASSWORD` only when set. `RestRedisCache` inherits the
same fail-fast `PING` contract as `RedisCache`.

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
  (`@Testcontainers(disabledWithoutDocker = true)`).
- **Publishing:** `pom.xml` configures `central-publishing-maven-plugin` with
  `deploymentName=ardoco-llm-access`, `autoPublish=true`, `waitUntil=published`. The
  `flatten-maven-plugin` version is pinned to `1.7.3` to avoid an NPE regression in later
  versions of the CI-friendly interpolator (see the comment in `pom.xml`).

## CI workflows (`.github/workflows/`)

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `verify.yml` | push (main, non-tag), PR, manual | Reusable Maven build/verify via `ardoco/actions` (no deploy). Ignores `docs/**` and `openwiki/**` paths. |
| `format.yml` | PR to main, manual | Runs `mvn spotless:apply` and auto-commits formatting changes. |
| `openwiki.yml` | weekly (Mon 06:00 UTC), manual | Delegates to the shared `ardoco/actions/.github/workflows/openwiki.yml@main` reusable workflow, forwarding `OPENROUTER_API_KEY`. The update logic (commit gating, `openwiki --update --print`, PR creation) is owned by that shared workflow, not inlined here. |

This repository's `openwiki.yml` only wires the reusable workflow and the `OPENROUTER_API_KEY`
secret; the commit-gating, `openwiki --update --print` invocation, and update-PR creation live
in the shared `ardoco/actions` workflow. The shared workflow gates on recent commits to avoid
churn: it skips when `origin/main` has no relevant (non-`openwiki/**`) commits in the last 7
days. Generated wiki content lives under `/openwiki`; `AGENTS.md` notes that the wiki is
optional just-in-time context and that source/tests are authoritative.
