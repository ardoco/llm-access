/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.llm.embedding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the framework-neutral embedding configuration API and the mock creator.
 */
@NullMarked
class EmbeddingConfigurationTest {

    @Test
    @DisplayName("of() sets platform and model")
    void testOf() {
        EmbeddingConfiguration config = EmbeddingConfiguration.of(EmbeddingPlatform.OPENAI, "text-embedding-3-large");
        assertEquals(EmbeddingPlatform.OPENAI, config.platform());
        assertEquals("text-embedding-3-large", config.modelName());
    }

    @Test
    @DisplayName("build requires a model name except for the mock platform")
    void testRequiresModel() {
        assertThrows(IllegalArgumentException.class, () -> EmbeddingConfiguration.builder(EmbeddingPlatform.OLLAMA).build());
        // The mock platform ignores the model, so it builds without one.
        assertEquals(EmbeddingPlatform.MOCK, EmbeddingConfiguration.builder(EmbeddingPlatform.MOCK).build().platform());
    }

    @Test
    @DisplayName("onnx() sets platform, model, and file paths")
    void testOnnxConfiguration() {
        EmbeddingConfiguration config = EmbeddingConfiguration.onnx("my-model", "/tmp/model.onnx", "/tmp/tokenizer.json");
        assertEquals(EmbeddingPlatform.ONNX, config.platform());
        assertEquals("my-model", config.modelName());
        assertEquals("/tmp/model.onnx", config.pathToModel());
        assertEquals("/tmp/tokenizer.json", config.pathToTokenizer());
    }

    @Test
    @DisplayName("fromString resolves embedding platforms case-insensitively")
    void testFromString() {
        assertEquals(EmbeddingPlatform.OPENWEBUI, EmbeddingPlatform.fromString("openwebui"));
        assertEquals(EmbeddingPlatform.MOCK, EmbeddingPlatform.fromString("Mock"));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingPlatform.fromString("nope"));
    }

    @Test
    @DisplayName("create() builds a mock creator returning zero vectors")
    void testMockCreator() {
        EmbeddingCreator creator = EmbeddingCreator.create(EmbeddingConfiguration.builder(EmbeddingPlatform.MOCK).build());
        List<float[]> embeddings = creator.calculateEmbeddings(List.of("a", "b"));
        assertEquals(2, embeddings.size());
        assertArrayEquals(new float[] { 0 }, embeddings.get(0));
        assertArrayEquals(new float[] { 0 }, embeddings.get(1));
        assertArrayEquals(new float[] { 0 }, creator.calculateEmbedding("single"));
    }

    @Test
    @DisplayName("create() requires ONNX file paths")
    void testOnnxRequiresPaths() {
        EmbeddingConfiguration incomplete = EmbeddingConfiguration.builder(EmbeddingPlatform.ONNX).modelName("m").build();
        assertThrows(NullPointerException.class, () -> EmbeddingCreator.create(incomplete));
    }
}
