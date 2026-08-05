---
type: Architecture
title: Cache Keys
description: The ChatCacheKey and ChatCacheParameter and EmbeddingCacheKey and EmbeddingCacheParameter implementations of CacheKey and CacheParameter, including backward-compatible file identifiers and the deprecated EmbeddingCacheKey.ofRaw.
tags: [cache, keys, domain]
openwiki:
  roles: [domain, architecture]
  change_kinds: [public-api]
  source_paths:
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/chat/ChatCacheKey.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/chat/ChatCacheParameter.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/embedding/EmbeddingCacheKey.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/cache/embedding/EmbeddingCacheParameter.java
  symbols: [ChatCacheKey, ChatCacheKey.of, ChatCacheParameter, ChatCacheParameter.parameters, EmbeddingCacheKey, EmbeddingCacheKey.of, EmbeddingCacheKey.ofRaw, EmbeddingCacheParameter]
  test_paths:
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/chat/ChatCacheKeyTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/cache/embedding/EmbeddingCacheKeyTest.java
  invariants:
    - ChatCacheParameter.parameters() omits temperature when 0.0 for backward compatibility.
    - EmbeddingCacheParameter.parameters() is the bare model name.
    - localKey is a content UUID from KeyGenerator and is excluded from toJsonKey.
    - The mode (CHAT/EMBEDDING) is part of the JSON key so the two domains never collide.
  validation_commands: ["mvn -q test -Dtest=ChatCacheKeyTest,EmbeddingCacheKeyTest"]
---

# Cache Keys

Concrete `CacheKey` and `CacheParameter` implementations for chat and embedding caching.
See [Cache Core](cache-core.md) for the abstractions. Both key types are
`@JsonAutoDetect(fieldVisibility = ANY)` with `@JsonInclude(NON_NULL)`, and both exclude
`localKey` from JSON via `@JsonIgnore`.

## ChatCacheParameter and ChatCacheKey

`ChatCacheParameter(String modelName, int seed, double temperature)` implements
`CacheParameter<ChatCacheKey>`.

- `parameters()` builds the file-name component. **For backward compatibility it omits the
  temperature when it is `0.0`**: `gpt-4o_42` for temperature 0.0, `gpt-4o_42_0.5` otherwise.
  This keeps existing LiSSA cache files compatible.
- `createCacheKey(content)` → `ChatCacheKey.of(this, content)`.

`ChatCacheKey` fields: `model`, `seed`, `temperature`, `mode` (always `CHAT`), `content`,
and `localKey` (a content UUID from [KeyGenerator](configuration.md)). `of` is
package-private and is the preferred constructor. `equals`/`hashCode` include `localKey`.

## EmbeddingCacheParameter and EmbeddingCacheKey

`EmbeddingCacheParameter(String modelName)` implements `CacheParameter<EmbeddingCacheKey>`.
Embeddings are deterministic, so only the model name identifies the cache:

- `parameters()` returns the bare model name (e.g. `text-embedding-ada-002`).
- `createCacheKey(content)` → `EmbeddingCacheKey.of(this, content)`.

`EmbeddingCacheKey` fields: `model`, `seed` (always `-1`), `temperature` (always `-1`),
`mode` (always `EMBEDDING`), `content`, and `localKey`. The `-1`/`-1` values are a
backward-compatibility marker.

`EmbeddingCacheKey.ofRaw(String model, String content, String localKey)` is **deprecated**
and exists only for the long-text recovery path in [Embeddings](embeddings.md), which stores
truncated-prefix embeddings under a custom `localKey` (`..._fixed_8000`). Prefer `of`. Tests
guard that `ofRaw` preserves the custom local key.

## Why mode is part of the key

Because `LargeLanguageModelCacheMode` (`CHAT` vs `EMBEDDING`) is a serialized field on both
keys, a chat entry and an embedding entry for the same model/content can never collide in
Redis (the hash key differs) or in local files (separate `<Origin>_<parameters>.json` files,
and `parameters()` differs: chat includes seed/temperature, embedding is just the model name).

## Focused tests

- `ChatCacheKeyTest` — `parametersOmitZeroTemperature`, `parametersIncludeTemperature`,
  `createCacheKey` (model/content/localKey), `jsonKeyExcludesLocalKey` (asserts `CHAT` and
  content are present, localKey absent), and `equality`.
- `EmbeddingCacheKeyTest` — `parametersAreModelName`, `createCacheKey`, `jsonKeyExcludesLocalKey`
  (asserts `EMBEDDING` present), `ofRawCustomLocalKey`, and `equality`.
