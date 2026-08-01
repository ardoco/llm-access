/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for creating vector embeddings of text content.
 * This class provides the interface for different embedding creation strategies,
 * which convert text into vector representations for similarity matching.
 * <p>
 * The framework supports multiple embedding creation backends (see {@link EmbeddingPlatform}):
 * <ul>
 * <li>Ollama: Local embedding generation using Ollama models
 * <ul>
 * <li>Requires OLLAMA_EMBEDDING_HOST environment variable</li>
 * <li>Optional authentication via OLLAMA_EMBEDDING_USER and OLLAMA_EMBEDDING_PASSWORD</li>
 * <li>Default model: nomic-embed-text:v1.5</li>
 * </ul>
 * </li>
 * <li>OpenAI: Cloud-based embedding generation using OpenAI's API
 * <ul>
 * <li>Requires OPENAI_ORGANIZATION_ID and OPENAI_API_KEY environment variables</li>
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
 * <li>Open WebUI: Embedding generation via an Open WebUI server</li>
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
     * Creates an appropriate embedding creator based on the provided configuration.
     *
     * @param configuration The configuration specifying which embedding creator to use
     * @return An instance of the appropriate embedding creator
     */
    public static EmbeddingCreator create(EmbeddingConfiguration configuration) {
        Objects.requireNonNull(configuration);
        return switch (configuration.platform()) {
            case OLLAMA -> new OllamaEmbeddingCreator(configuration.modelName());
            case OPENAI -> new OpenAiEmbeddingCreator(configuration.modelName());
            case ONNX -> new OnnxEmbeddingCreator(configuration.modelName(), Objects.requireNonNull(configuration.pathToModel(),
                    "pathToModel is required for ONNX"), Objects.requireNonNull(configuration.pathToTokenizer(), "pathToTokenizer is required for ONNX"));
            case OPENWEBUI -> new OpenWebUiEmbeddingCreator(configuration.modelName());
            case MOCK -> new MockEmbeddingCreator();
        };
    }
}
