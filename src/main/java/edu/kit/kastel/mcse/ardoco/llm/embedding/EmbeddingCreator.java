/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.util.List;
import java.util.Objects;

import edu.kit.kastel.mcse.ardoco.llm.cache.CacheManager;

/**
 * Abstract base class for creating vector embeddings of text content.
 * This class provides the interface for different embedding creation strategies,
 * which convert text into vector representations for similarity matching.
 * <p>
 * The framework supports multiple embedding creation backends (see {@link EmbeddingPlatform}). The environment variables
 * named below are read from the {@link EmbeddingConfiguration#environment() environment} of the configuration:
 * <ul>
 * <li>Ollama: Embedding generation using models served by an Ollama server
 * <ul>
 * <li>Requires OLLAMA_EMBEDDING_HOST environment variable</li>
 * <li>Optional authentication via OLLAMA_EMBEDDING_USER and OLLAMA_EMBEDDING_PASSWORD</li>
 * <li>Default model: nomic-embed-text:v1.5</li>
 * </ul>
 * </li>
 * <li>OpenAI: Cloud-based embedding generation using OpenAI's API
 * <ul>
 * <li>Requires the OPENAI_API_KEY environment variable; OPENAI_ORGANIZATION_ID is optional</li>
 * <li>Default model: text-embedding-ada-002</li>
 * <li>Supports high-throughput with 40 parallel threads</li>
 * </ul>
 * </li>
 * <li>ONNX: Local embedding generation using ONNX models
 * <ul>
 * <li>Requires local model and tokenizer files</li>
 * <li>Uses mean pooling for embedding generation</li>
 * </ul>
 * </li>
 * <li>Open WebUI: Embedding generation via an Open WebUI server
 * <ul>
 * <li>Requires OPENWEBUI_URL and OPENWEBUI_API_KEY environment variables</li>
 * </ul>
 * </li>
 * <li>Mock: Testing implementation returning zero vectors for all inputs</li>
 * </ul>
 *
 * All implementations (except Mock) support caching of embeddings through the
 * {@link CachedEmbeddingCreator} base class, which provides:
 * <ul>
 * <li>Automatic caching of generated embeddings</li>
 * <li>Handling of long texts through token length management</li>
 * <li>Multi-threaded embedding generation where supported</li>
 * <li>Fallback mechanisms for failed embedding generation</li>
 * </ul>
 */
public abstract class EmbeddingCreator {

    /**
     * Creates a new embedding creator.
     */
    protected EmbeddingCreator() {
        // No shared state.
    }

    /**
     * Calculates the embedding for a single piece of content.
     * This is a convenience method that delegates to {@link #calculateEmbeddings(List)}.
     *
     * @param content The content to create an embedding for
     * @return The vector embedding of the content
     */
    public float[] calculateEmbedding(String content) {
        return calculateEmbeddings(List.of(content)).getFirst();
    }

    /**
     * Calculates embeddings for a list of content strings.
     * This method must be implemented by concrete embedding creators to provide
     * the actual embedding generation logic.
     *
     * @param contents The list of content strings to create embeddings for
     * @return A list of vector embeddings, in the same order as the input content
     */
    public abstract List<float[]> calculateEmbeddings(List<String> contents);

    /**
     * Creates an appropriate embedding creator based on the provided configuration, caching the embeddings in a cache
     * of the given cache manager. Credentials and host URLs are read from the configuration's
     * {@link EmbeddingConfiguration#environment() environment}, while the cache settings come from the cache manager.
     *
     * @param configuration The configuration specifying which embedding creator to use
     * @param cacheManager  The cache manager that provides the embedding cache (not used by the mock platform)
     * @return An instance of the appropriate embedding creator
     */
    public static EmbeddingCreator create(EmbeddingConfiguration configuration, CacheManager cacheManager) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(cacheManager, "cacheManager must not be null");
        String model = configuration.modelName();
        return switch (configuration.platform()) {
            case OLLAMA -> new OllamaEmbeddingCreator(model, configuration.environment(), cacheManager);
            case OPENAI -> new OpenAiEmbeddingCreator(model, configuration.environment(), cacheManager);
            case ONNX -> new OnnxEmbeddingCreator(model, Objects.requireNonNull(configuration.pathToModel(), "pathToModel is required for ONNX"), Objects
                    .requireNonNull(configuration.pathToTokenizer(), "pathToTokenizer is required for ONNX"), configuration.environment(), cacheManager);
            case OPENWEBUI -> new OpenWebUiEmbeddingCreator(model, configuration.environment(), cacheManager);
            case MOCK -> new MockEmbeddingCreator();
        };
    }

    /**
     * Creates an appropriate embedding creator based on the provided configuration, caching the embeddings in a cache
     * of the {@link CacheManager#getDefaultInstance() default cache manager}. This is a convenience for
     * {@link #create(EmbeddingConfiguration, CacheManager)}; the mock platform does not require the default cache
     * manager to be set up.
     *
     * @param configuration The configuration specifying which embedding creator to use
     * @return An instance of the appropriate embedding creator
     * @throws IllegalStateException If a caching platform is configured and the default cache manager is not set up
     */
    public static EmbeddingCreator create(EmbeddingConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (configuration.platform() == EmbeddingPlatform.MOCK) {
            return new MockEmbeddingCreator();
        }
        return create(configuration, CacheManager.getDefaultInstance());
    }
}
