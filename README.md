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
<version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Chat model

```java
import edu.kit.kastel.mcse.ardoco.llm.chat.*;
import dev.langchain4j.model.chat.ChatModel;

LlmConfiguration config = LlmConfiguration.builder(ChatModelPlatform.OPENAI)
		.modelName("gpt-4o-mini") // optional; defaults per platform
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
ChatModelProvider provider = new ChatModelProvider(LlmConfiguration.of(ChatModelPlatform.OPENAI));

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
EmbeddingCreator creator = EmbeddingCreator.create(EmbeddingConfiguration.of(EmbeddingPlatform.OPENAI));

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

The cache is configured through environment variables and read by `CacheManager`:

- `CACHE_HIERARCHY` — comma-separated list of cache types, primary first (default `LOCAL`). Valid
types: `LOCAL`, `REDIS`, `REST_REDIS`. Example: `REDIS,LOCAL` uses Redis as the primary layer with a
local file fallback.
- `CACHE_REPLACEMENT_STRATEGY` — how to resolve conflicts between layers (default `NONE`). One of:
- `NONE` — return the primary value; backfill a layer that is missing a value.
- `ERROR` — throw if two layers disagree on a key.
- `OVERWRITE` — overwrite the secondary layer with the primary value on conflict.
- `REDIS_URL` — Redis connection URL (default `redis://localhost:6379`).
- `REST_REDIS_URI`, `REST_REDIS_USERNAME`, `REST_REDIS_PASSWORD` — REST-Redis connection.

Local caches are stored as JSON files named `<Origin>_<parameters>.json` inside the configured cache
directory.

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
