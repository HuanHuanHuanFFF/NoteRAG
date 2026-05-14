package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.EmbeddingProperties;
import com.huanf.noterag.mapper.EmbeddingModelMapper;
import com.huanf.noterag.model.EmbeddingModel;

class EmbeddingModelResolverTests {

    private final EmbeddingProperties embeddingProperties = embeddingProperties(1024);
    private final EmbeddingModelMapper embeddingModelMapper = mock(EmbeddingModelMapper.class);
    private final EmbeddingModelResolver embeddingModelResolver = new EmbeddingModelResolver(
            embeddingProperties,
            embeddingModelMapper);

    @Test
    void resolveRequired1024ModelReturnsEnabledModel() {
        EmbeddingModel model = embeddingModel(7L, 1024);
        when(embeddingModelMapper.findEnabledBySpec("openai", "text-embedding-v4", 1024, "cosine"))
                .thenReturn(model);

        EmbeddingModel resolved = embeddingModelResolver.resolveRequired1024Model();

        assertThat(resolved).isSameAs(model);
        verify(embeddingModelMapper).findEnabledBySpec("openai", "text-embedding-v4", 1024, "cosine");
    }

    @Test
    void resolveRequired1024ModelFailsWhenEmbeddingIsDisabled() {
        embeddingProperties.setEnabled(false);

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_CONFIG_INVALID);
                    assertThat(exception).hasMessage("Embedding is disabled. Set noterag.embedding.enabled=true before using embedding features.");
                });
        verifyNoInteractions(embeddingModelMapper);
    }

    @Test
    void resolveRequired1024ModelFailsWhenConfigIsIncomplete() {
        embeddingProperties.setModelName(" ");

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_CONFIG_INVALID);
                    assertThat(exception).hasMessage("Embedding config is invalid: provider, modelName, distanceMetric must be set and dimension must be greater than 0");
                });
        verifyNoInteractions(embeddingModelMapper);
    }

    @Test
    void resolveRequired1024ModelFailsWhenConfiguredDimensionIsNot1024() {
        embeddingProperties.setDimension(1536);

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_DIMENSION_UNSUPPORTED);
                    assertThat(exception).hasMessage("Only 1024-dimension embeddings are supported currently, configured dimension=1536");
                });
        verifyNoInteractions(embeddingModelMapper);
    }

    @Test
    void resolveRequired1024ModelFailsWhenEnabledModelIsMissing() {
        when(embeddingModelMapper.findEnabledBySpec("openai", "text-embedding-v4", 1024, "cosine"))
                .thenReturn(null);

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_MODEL_NOT_FOUND);
                    assertThat(exception).hasMessageContaining("Enabled embedding model config not found");
                });
    }

    @Test
    void resolveRequired1024ModelFailsWhenModelIdIsMissing() {
        when(embeddingModelMapper.findEnabledBySpec("openai", "text-embedding-v4", 1024, "cosine"))
                .thenReturn(embeddingModel(null, 1024));

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_MODEL_NOT_FOUND);
                    assertThat(exception).hasMessage("Embedding model config id must not be null");
                });
    }

    @Test
    void resolveRequired1024ModelFailsWhenModelDimensionMismatchesConfig() {
        when(embeddingModelMapper.findEnabledBySpec("openai", "text-embedding-v4", 1024, "cosine"))
                .thenReturn(embeddingModel(7L, 512));

        assertThatThrownBy(embeddingModelResolver::resolveRequired1024Model)
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_RESULT_INVALID);
                    assertThat(exception).hasMessage("Embedding model dimension mismatch: expected=1024, actual=512");
                });
    }

    private static EmbeddingProperties embeddingProperties(int dimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setModelName("text-embedding-v4");
        properties.setDimension(dimension);
        properties.setDistanceMetric("cosine");
        return properties;
    }

    private static EmbeddingModel embeddingModel(Long id, int dimension) {
        EmbeddingModel model = new EmbeddingModel();
        model.setId(id);
        model.setDimension(dimension);
        return model;
    }
}
