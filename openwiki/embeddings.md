---
type: Architecture
title: Embeddings
description: The EmbeddingCreator hierarchy for OpenAI, Ollama, ONNX, Open WebUI, and Mock embeddings, including CachedEmbeddingCreator caching, parallel execution, and long-text recovery.
tags: [embedding, cache, architecture]
openwiki:
  roles: [architecture, domain, workflow]
  change_kinds: [public-api, lifecycle]
  source_paths:
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/embedding/EmbeddingCreator.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/embedding/CachedEmbeddingCreator.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/embedding/EmbeddingConfiguration.java
    - src/main/java/edu/kit/kastel/mcse/ardoco/llm/embedding/EmbeddingPlatform.java
  symbols: [EmbeddingCreator, EmbeddingCreator.create, CachedEmbeddingCreator, CachedEmbeddingCreator.calculateEmbeddings, EmbeddingConfiguration, EmbeddingPlatform]
  test_paths:
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/embedding/CachedEmbeddingCreatorTest.java
    - src/test/java/edu/kit/kastel/mcse/ardoco/llm/embedding/EmbeddingConfigurationTest.java
  invariants:
    - EmbeddingCreator.create dispatches on EmbeddingPlatform; MOCK skips caching.
    - CachedEmbeddingCreator obtains its Cache from CacheManager.getDefaultInstance() in the constructor (cache dir must be set first).
    - calculateEmbeddings returns a list in input order; empty input returns an empty list with no model call.
    - Long text exceeding MAX_TOKEN_LENGTH (8000) is binary-searched to a fitting prefix and cached under a _fixed_ key.
  validation_commands: ["mvn -q test -Dtest=CachedEmbeddingCreatorTest,EmbeddingConfigurationTest"]
---

# Embeddings

The embedding subsystem turns an `EmbeddingConfiguration` into an `EmbeddingCreator` that
produces `float[]` vectors. Caching creators store every computed embedding in a
`Cache<EmbeddingCacheKey>` obtained from [CacheManager](cache-hierarchy.md); the mock
creator skips caching entirely.

## EmbeddingConfiguration and EmbeddingPlatform

`EmbeddingConfiguration(EmbeddingPlatform platform, String modelName, @Nullable String pathToModel,
@Nullable String pathToTokenizer)` is a record. `of(platform, modelName)` is the simple form;
`onnx(model, pathToModel, pathToTokenizer)` creates an ONNX config with local file paths.
The builder requires a non-blank `modelName` for all non-MOCK platforms (MOCK defaults to
`"mock"` when blank).

`EmbeddingPlatform` is an enum: `OLLAMA`, `OPENAI`, `ONNX`, `OPENWEBUI`, `MOCK`. `fromString`
is case-insensitive and throws for unknown names.

## EmbeddingCreator factory

`EmbeddingCreator.create(EmbeddingConfiguration)` dispatches on `platform()`:

| Platform | Creator | Threads | Notes |
| --- | --- | --- | --- |
| `OLLAMA` | `OllamaEmbeddingCreator` | 1 | Requires `OLLAMA_EMBEDDING_HOST`; optional `OLLAMA_EMBEDDING_USER`/`_PASSWORD`. 5-min timeout. |
| `OPENAI` | `OpenAiEmbeddingCreator` | 40 | Requires `OPENAI_API_KEY`; optional `OPENAI_ORGANIZATION_ID`; `maxRetries(0)`. |
| `ONNX` | `OnnxEmbeddingCreator` | 1 | Requires existing `pathToModel` and `pathToTokenizer` files; `PoolingMode.MEAN`. |
| `OPENWEBUI` | `OpenWebUiEmbeddingCreator` | 1 | Requires `OPENWEBUI_URL` and `OPENWEBUI_API_KEY`; OpenAI-compatible. 5-min timeout. |
| `MOCK` | `MockEmbeddingCreator` | — | Returns `new float[]{0}` per input; no caching, no env. |

`calculateEmbedding(String)` delegates to `calculateEmbeddings(List.of(content)).getFirst()`.

## CachedEmbeddingCreator

All real creators extend `CachedEmbeddingCreator`, an abstract template-method base that
owns caching and parallelism. On construction it:

1. Builds `EmbeddingCacheParameter(model)` and obtains a `Cache<EmbeddingCacheKey>` from
   `CacheManager.getDefaultInstance().getCache(this, ...)` — so [CacheManager.setCacheDir](cache-hierarchy.md)
   must have been called first.
2. Calls the abstract `createEmbeddingModel(model, params...)` to build the primary
   `EmbeddingModel` eagerly.
3. Clamps `threads` to at least 1.

`calculateEmbeddings(List<String>)` is `final` and dispatches:

- Empty input → empty list, no model call.
- `threads == 1` → sequential.
- `threads > 1` → splits input across a fixed thread pool of size `min(threads, contents.size())`;
  each thread builds its **own** `EmbeddingModel` via `createEmbeddingModel` and processes its
  sublist; results are joined with `Futures.getLogged` (see [Configuration and Environment](configuration.md))
  and flattened in order.

Per-content flow (`calculateFinalEmbedding`):

1. Cache hit → return cached vector.
2. Miss → `embeddingModel.embed(content).content().vector()`, cache it, return.
3. On exception → `tryToFixWithLength` (below).

### Long-text recovery (`tryToFixWithLength`)

When an embed call fails, the fallback assumes the content exceeded the model token limit.
`MAX_TOKEN_LENGTH = 8000` (a conservative bound; the source comment notes 8192 for ada).

- Look up a cached fixed embedding under the key `originalKey.localKey() + "_fixed_" + MAX_TOKEN_LENGTH`
  (created via the deprecated `EmbeddingCacheKey.ofRaw` for backward compatibility — see
  [Cache Keys](cache-keys.md)).
- Use jtokkit (`Encodings`/`EncodingRegistry`) to count tokens. If `tokens < MAX_TOKEN_LENGTH`,
  throw `IllegalArgumentException` — the failure was not length-related.
- Otherwise binary-search for the longest substring whose token count fits, embed that
  prefix, and cache it under the fixed key.

## Adding an embedding platform

1. Add a constant to `EmbeddingPlatform`.
2. Create a subclass of `CachedEmbeddingCreator` (or `EmbeddingCreator` for a non-caching
   one) implementing `createEmbeddingModel`. Pass the desired thread count to the superclass
   constructor; pass extra builder inputs as `params`.
3. Add a `case` to `EmbeddingCreator.create` and read credentials via [Environment](configuration.md).
4. Extend `EmbeddingConfigurationTest` and `CachedEmbeddingCreatorTest` (the latter uses a
   `RecordingEmbeddingCreator` to assert caching, order, empty input, persistence, and that
   the parallel path forwards `params` to every per-thread model).

## Focused tests

- `CachedEmbeddingCreatorTest` — `cachesEmbeddings` (second call is a cache hit),
  `preservesOrder`, `emptyInput` (no model call), `persistsAcrossReload` (flush + reload),
  `parallelPathForwardsParameters` (per-thread models receive `["p1","p2"]`).
- `EmbeddingConfigurationTest` — `of`/`onnx`/builder validation, `fromString`, the MOCK
  creator path, and ONNX missing-paths failure.

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
flowchart TD
    A["calculateEmbeddings(contents)"] --> B{"contents empty?"}
    B -- yes --> E["return empty list"]
    B -- no --> C{"threads > 1?"}
    C -- yes --> D["split across thread pool, per-thread model"]
    C -- no --> F["sequential: per content"]
    D --> F
    F --> G{"cache.get(content) hit?"}
    G -- yes --> H["return cached vector"]
    G -- no --> I["model.embed(content)"]
    I -- success --> J["cache.put(content), return"]
    I -- exception --> K["tryToFixWithLength"]
    K --> L{"tokens >= MAX_TOKEN_LENGTH?"}
    L -- no --> M["throw IllegalArgumentException"]
    L -- yes --> N["binary search prefix, embed, cache _fixed_ key"]
```
*Embedding execution path: caching, parallel split, and long-text recovery.*
