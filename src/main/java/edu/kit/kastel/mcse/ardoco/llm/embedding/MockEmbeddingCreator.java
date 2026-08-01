/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import java.util.List;

/**
 * A mock implementation of the embedding creator that returns zero vectors for all inputs.
 * This is useful for testing the embedding pipeline without incurring the computational cost of
 * real embedding generation, or in ensemble scenarios where actual embeddings are not required.
 * <p>
 * Each input is assigned a zero vector of length 1.
 */
public class MockEmbeddingCreator extends EmbeddingCreator {

    /**
     * Creates a new mock embedding creator.
     */
    public MockEmbeddingCreator() {
        // No configuration required.
    }

    /**
     * Calculates mock embeddings for a list of content strings.
     * Each input is assigned a zero vector of length 1.
     *
     * @param contents The list of content strings to create mock embeddings for
     * @return A list of zero vectors, one for each input
     */
    @Override
    public List<float[]> calculateEmbeddings(List<String> contents) {
        return contents.stream().map(it -> new float[] { 0 }).toList();
    }
}
