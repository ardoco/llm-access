# ARDoCo LLM Access

A small, reusable Java library for accessing Large Language Models (LLMs) and embedding models through
[LangChain4j](https://docs.langchain4j.dev/), with a pluggable caching layer for LLM requests and
embeddings.

It is framework-neutral: model settings are passed as plain configuration objects (no coupling to any
particular application's config format), while credentials and hosts are read from the environment. The
code was extracted and generalized from the [LiSSA](https://github.com/ardoco/lissa) project so that
LiSSA, [ardoco](https://github.com/ardoco), and other tools can share one implementation.

## Features

- **Chat models** for OpenAI, Ollama, Blablador, DeepSeek, and Open WebUI, created lazily and
configured via a typed builder.
- **Cached requests**: single or n-fold LLM calls, or a transparent `CachingChatModel` decorator, backed by a cache.
- **Embeddings** for OpenAI, Ollama, ONNX, and Open WebUI (plus a mock), with automatic caching and
token-length handling.
- **Pluggable cache** with local-file, Redis, and REST-Redis backends, hierarchical layering, and
conflict-resolution strategies. The on-disk format is compatible with LiSSA's existing caches.

## Requirements

- Java 21+
- Maven

## Installation

```xml
<dependency>
<groupId>io.github.ardoco</groupId>
<artifactId>llm-access</artifactId>
<version>VERSION</version>
</dependency>
```

## Usage

### Chat model

```java
import edu.kit.kastel.mcse.ardoco.llm.chat.*;
import dev.langchain4j.model.chat.ChatModel;

LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OPENAI)
		.modelName("gpt-4o-mini") // required
		.seed(133742243)          // optional
		.temperature(0.0)         // optional
		.build();

ChatModel model = new ChatModelProvider(config).createChatModel();
String answer = model.chat("Hello!");
```

### Cached requests

Wrap requests in a cache so repeated prompts are not re-sent to the model:

```java
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.cache.Cache;
import edu.kit.kastel.mcse.ardoco.llm.cache.chat.ChatCacheKey;
import edu.kit.kastel.mcse.ardoco.llm.chat.*;

CacheManager.setCacheDir("cache");
ChatModelProvider provider = new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.OPENAI, "gpt-4o-mini"));

Cache<ChatCacheKey> cache = CacheManager.getDefaultInstance().getCache(provider, provider.cacheParameters());
ChatModel model = provider.createChatModel();

String once = ChatModelUtils.cachedRequest("Summarize X", model, cache);
var many = ChatModelUtils.nCachedRequest("Summarize X", model, cache, 5); // 5 samples

cache.flush(); // persist
```

Alternatively, wrap any `ChatModel` in a `CachingChatModel` decorator to cache transparently (including
multi-message chats) without changing call sites:

```java
ChatModel cached = new CachingChatModel(provider.createChatModel(), cache);
cached.chat(List.of(UserMessage.from("Summarize X"))); // response cached by message content
```

### Embeddings

```java
import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;
import edu.kit.kastel.mcse.ardoco.llm.embedding.*;

CacheManager.setCacheDir("cache"); // required for the caching creators
EmbeddingCreator creator = EmbeddingCreator.create(EmbeddingConfiguration.of(EmbeddingPlatform.OPENAI, "text-embedding-3-large"));

float[] vector = creator.calculateEmbedding("some text");
var vectors = creator.calculateEmbeddings(List.of("a", "b", "c"));
```

ONNX models need local files:

```java
EmbeddingCreator creator = EmbeddingCreator.create(
		EmbeddingConfiguration.onnx("bge-small", "/path/model.onnx", "/path/tokenizer.json"));
```

## Configuration

Credentials and hosts are read via `Environment`, which loads a `.env` file from the working directory
(falling back to system environment variables). See [`sample.env`](sample.env) for a template.

| Platform   | Chat env vars                                                      | Embedding env vars                                                |
| ---------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| OpenAI     | `OPENAI_API_KEY` (`OPENAI_ORGANIZATION_ID` optional)               | `OPENAI_API_KEY` (`OPENAI_ORGANIZATION_ID` optional)              |
| Ollama     | `OLLAMA_HOST` (`OLLAMA_USER`+`OLLAMA_PASSWORD`, or `OLLAMA_TOKEN`) | `OLLAMA_EMBEDDING_HOST` (`OLLAMA_EMBEDDING_USER`, `..._PASSWORD`) |
| Blablador  | `BLABLADOR_API_KEY`                                                | —                                                                 |
| DeepSeek   | `DEEPSEEK_API_KEY`                                                 | —                                                                 |
| Open WebUI | `OPENWEBUI_URL`, `OPENWEBUI_API_KEY`                               | `OPENWEBUI_URL`, `OPENWEBUI_API_KEY`                              |

## Caching

Caching is central to how this library is meant to be used: with a fixed `seed` and `temperature`,
identical requests are served from the cache instead of being re-sent to the model, which makes runs
reproducible and keeps API cost and latency down. A `CacheManager` owns the configured backend(s) and
hands out `Cache` instances; the model wrappers (`CachingChatModel`, the caching embedding creators) read
and write through them automatically.

Before using the default manager, set the cache directory once:

```java
CacheManager.setCacheDir("cache"); // getDefaultInstance() throws until this is called
```

All cache behaviour (which backends, layering, conflict handling, connection details) is driven by
environment variables, read when the `CacheManager` is constructed.

### How entries are identified

Every entry is keyed by the model configuration (`model`, `seed`, `temperature`), the mode (`CHAT` vs
`EMBEDDING`), and the request content. Because the mode is part of the key, chat and embedding entries
never collide, and different models/seeds/temperatures are kept apart automatically.

- **Local (file) cache:** one JSON file per caller and model configuration, named
`<Origin>_<model>_<seed>[_<temperature>].json` in the cache directory. `<Origin>` is the simple class
name of the object that requested the cache, and `<temperature>` is omitted when it is `0.0` (kept for
backward compatibility with LiSSA's existing caches). Within a file, content is mapped to values via a
UUID derived from the content.
- **Redis / REST-Redis:** each entry is a Redis hash whose key is the JSON form of the cache key, with
fields `data` (the stored value) and `timestamp`.

### Backends

| Type         | Storage                                | What you deploy                              | Use when                                                                                                  |
| ------------ | -------------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `LOCAL`      | JSON files in the cache directory      | nothing                                      | single-machine or developer runs (the default)                                                            |
| `REDIS`      | a Redis server, direct TCP (via Jedis) | a Redis server, reachable on its port        | a cache shared across machines/runs on a trusted network                                                  |
| `REST_REDIS` | a Redis server behind an HTTP proxy    | a Redis server **and** the REST-Redis server | Redis is not reachable directly (firewall / HTTP-only egress) or you want HTTP auth in front of the cache |

Redis and REST-Redis **fail fast**: if the backend cannot be reached at start-up (its `PING` fails), cache
creation throws instead of silently falling back. Pair a remote backend with `LOCAL` (see below) if you
want a local fallback layer.

### Hierarchy and conflict resolution

`CACHE_HIERARCHY` is a comma-separated list of backends, **primary first**. A single entry (e.g. `LOCAL`)
means no layering; multiple entries stack the caches, with the first as the primary layer and the rest as
fallbacks:

```
CACHE_HIERARCHY=REDIS,LOCAL        # read/write Redis first, fall back to a local file layer
CACHE_HIERARCHY=REST_REDIS,LOCAL   # same, but reach Redis over HTTP
```

Writes are **write-through**: every `put` stores the response in all layers at once. On a read, all layers
are consulted, and a value present in one layer but missing from another is copied into the layer that lacks
it (**backfill**) — in either direction. `CACHE_REPLACEMENT_STRATEGY` decides what happens when two layers
hold **different** values for the same key:

| Strategy    | Behaviour                                                                |
| ----------- | ------------------------------------------------------------------------ |
| `NONE`      | (default) return the primary value; backfill layers that are missing it  |
| `ERROR`     | throw `IllegalStateException` if two layers disagree on a key            |
| `OVERWRITE` | overwrite the secondary layer with the primary value on conflict         |

### Configuration reference

| Variable                     | Applies to     | Default                  | Description                                                               |
| ---------------------------- | -------------- | ------------------------ | ------------------------------------------------------------------------- |
| `CACHE_HIERARCHY`            | all            | `LOCAL`                  | Comma-separated backends, primary first (`LOCAL`, `REDIS`, `REST_REDIS`)  |
| `CACHE_REPLACEMENT_STRATEGY` | layered caches | `NONE`                   | Conflict handling between layers: `NONE`, `ERROR`, `OVERWRITE`            |
| `REDIS_URL`                  | `REDIS`        | `redis://localhost:6379` | Redis connection URL                                                      |
| `REST_REDIS_URI`             | `REST_REDIS`   | `http://localhost:8080`  | Base URL of the REST-Redis server (or a proxy in front of it)             |
| `REST_REDIS_USERNAME`        | `REST_REDIS`   | —                        | HTTP Basic-auth username (optional; sent only when set)                   |
| `REST_REDIS_PASSWORD`        | `REST_REDIS`   | —                        | HTTP Basic-auth password (optional; sent only when set)                   |

The cache **directory** is set in code via `CacheManager.setCacheDir(...)`, not through an environment
variable — pick where that value comes from in your own runner (e.g. a `LLM_CACHE_DIR` variable you read
and pass in).

### Deployment

#### Local file cache

Nothing to deploy: the default `CACHE_HIERARCHY=LOCAL` writes JSON files into the directory passed to
`CacheManager.setCacheDir(...)`. These files are self-contained, which makes `LOCAL` the format used for
**replication packages** — commit or share the directory and others can reproduce a run offline (see
[Replication packages](#replication-packages) below).

#### Deploying Redis

Use the `REDIS` backend when several machines or runs should share one cache over a trusted network.

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

Then point clients at it:

```env
CACHE_HIERARCHY=REDIS,LOCAL
REDIS_URL=redis://redis-host:6379
```

Redis has no authentication by default. If it is reachable beyond a trusted network, enable a password
(`--requirepass`) / TLS and put credentials in the URL (`rediss://user:pass@host:6380`).

#### Deploying REST-Redis

REST-Redis exists for the case where clients can only reach the cache over **HTTP**, not over the raw Redis
TCP port (e.g. a shared team cache behind a reverse proxy, or restricted egress). It is a thin HTTP server
([`org.fuchss:rest-redis`](https://central.sonatype.com/artifact/org.fuchss/rest-redis)) that proxies the
handful of operations the cache actually uses (`ping`, `exists`, `hget`, `hset`) to a real Redis. The
matching client is already bundled in this library, so applications only need `REST_REDIS_URI`.

The topology is:

```
client (this library) --HTTP--> [reverse proxy: TLS + auth] --HTTP--> REST-Redis server --TCP--> Redis
```

The server is published as a Docker image
([`ghcr.io/dfuchss/rest-redis`](https://github.com/dfuchss/rest-redis)), so deployment is just Docker:

1. **Write the server config** as `server_config.json`. It points the server at your Redis and picks the
HTTP port to serve on:

```json
{
	"redis_host": "redis",
	"redis_port": 6379,
	"http_port": 8080
}
```

2. **Run Redis and the REST-Redis server** together with Docker Compose:

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
		- "8080:8080" # expose directly only on a trusted network — otherwise front it with a proxy (see below)

volumes:
	redis-data:
```

To run only the server against an existing Redis, use the image directly:

```bash
docker run -p 8080:8080 -v "$(pwd)/server_config.json:/app/server_config.json:ro" ghcr.io/dfuchss/rest-redis
```

3. **Point clients at it:**

```env
CACHE_HIERARCHY=REST_REDIS,LOCAL
REST_REDIS_URI=http://rest-redis-host:8080
# REST_REDIS_USERNAME / REST_REDIS_PASSWORD — only when a proxy in front enforces Basic auth
```

The server has **no built-in authentication or TLS**. When exposing it beyond a trusted network, put a
reverse proxy (nginx, Caddy, Traefik, …) in front to terminate TLS and Basic auth, point `REST_REDIS_URI`
at the proxy, and set `REST_REDIS_USERNAME` / `REST_REDIS_PASSWORD` — the client sends them only when set.

### Replication packages

Because `LOCAL` cache files are self-contained, the cache directory _is_ the replication artifact: ship it
and anyone can reproduce a run **offline** — no API keys, no Redis, no model access — by pointing their
`CacheManager` at it with `CACHE_HIERARCHY=LOCAL`.

To fill that local cache while running experiments against a shared Redis, layer `LOCAL` underneath it:

```env
CACHE_HIERARCHY=REDIS,LOCAL   # or REST_REDIS,LOCAL
```

With this layering every response is write-through to both Redis and the local files, and any entry already
in Redis (e.g. from an earlier run or a teammate) is backfilled into the local files the first time this run
reads it. So after the experiment the local cache directory holds every request the run touched. Flush at
the end (`CacheManager.getDefaultInstance().flush()`) to make sure everything is on disk, then ship that
directory as the replication package. Replicators unpack it, set `CACHE_HIERARCHY=LOCAL`, and re-run.

### Choosing a backend

- **Just running locally / developing, or shipping a replication package?** Keep the default `LOCAL`.
- **Sharing a cache across machines on a trusted network (and/or building up a replication package)?**
`REDIS,LOCAL`.
- **Clients can only talk HTTP, or you need auth/TLS in front of the cache?** `REST_REDIS,LOCAL`.

## Package overview

| Package                                          | Contents                                                     |
| ------------------------------------------------ | ------------------------------------------------------------ |
| `edu.kit.kastel.mcse.ardoco.llm.chat`            | Chat model providers, platforms, lazy model, cached requests |
| `edu.kit.kastel.mcse.ardoco.llm.embedding`       | Embedding creators and configuration                         |
| `edu.kit.kastel.mcse.ardoco.llm.cache`           | Cache abstraction, backends, hierarchy, and manager          |
| `edu.kit.kastel.mcse.ardoco.llm.cache.chat`      | Typed cache keys/parameters for chat requests                |
| `edu.kit.kastel.mcse.ardoco.llm.cache.embedding` | Typed cache keys/parameters for embeddings                   |
| `edu.kit.kastel.mcse.ardoco.llm.util`            | Environment/.env access, key generation, helpers             |

## Building

```bash
mvn verify
```

Tests that require Docker (the Testcontainers-based REST-Redis integration test) are skipped
automatically when no Docker environment is available.

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
