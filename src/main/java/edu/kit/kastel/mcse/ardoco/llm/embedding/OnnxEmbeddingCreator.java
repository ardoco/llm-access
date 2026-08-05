/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.io.File;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;

/**
 * An embedding creator that uses ONNX models for generating embeddings.
 * This allows for local embedding generation without requiring external services.
 * <p>
 * The creator requires both a model file and a tokenizer file to be present on the local filesystem.
 * The embedding model uses mean pooling by default for generating the final embeddings.
 */
public class OnnxEmbeddingCreator extends CachedEmbeddingCreator {

    /**
     * Creates a new ONNX embedding creator with the specified model and file paths.
     *
     * @param model           The name of the model (used for tokenizer selection and cache identification)
     * @param pathToModel     The path to the ONNX model file
     * @param pathToTokenizer The path to the tokenizer file
     */
    public OnnxEmbeddingCreator(String model, String pathToModel, String pathToTokenizer) {
        super(model, 1, pathToModel, pathToTokenizer);
    }

    /**
     * Creates an ONNX embedding model instance with the specified parameters.
     * The method verifies the existence of both the model and tokenizer files before creating the model.
     *
     * @param model  The name of the model
     * @param params Additional parameters containing the model and tokenizer file paths
     * @return A configured ONNX embedding model instance
     * @throws IllegalStateException If either the model or tokenizer file does not exist
     */
    @Override
    protected EmbeddingModel createEmbeddingModel(String model, String... params) {
        String modelPath = params[0];
        String tokenizerPath = params[1];

        File modelFile = new File(modelPath);
        File tokenizerFile = new File(tokenizerPath);
        if (!modelFile.exists() || !tokenizerFile.exists()) {
            throw new IllegalStateException("Model or Tokenizer file does not exist");
        }

        PoolingMode poolingMode = PoolingMode.MEAN;
        EmbeddingModel embeddingModel = new OnnxEmbeddingModel(modelFile.toPath(), tokenizerFile.toPath(), poolingMode);
        logger.info("Created OnnxEmbeddingModel with model: {} and tokenizer: {}", modelPath, tokenizerPath);
        return embeddingModel;
    }
}
