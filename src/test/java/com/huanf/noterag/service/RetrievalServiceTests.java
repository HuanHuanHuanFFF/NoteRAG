package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RetrievalProperties;
import com.huanf.noterag.mapper.ChunkRetrievalMapper;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.RetrievedChunk;

class RetrievalServiceTests {

    private final RetrievalProperties retrievalProperties = retrievalProperties(20, 50);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final EmbeddingModelResolver embeddingModelResolver = mock(EmbeddingModelResolver.class);
    private final ChunkRetrievalMapper chunkRetrievalMapper = mock(ChunkRetrievalMapper.class);
    private final RetrievalService retrievalService = new RetrievalService(
            embeddingClient,
            embeddingModelResolver,
            chunkRetrievalMapper,
            retrievalProperties);

    @Test
    void retrieveTopNStripsQuestionEmbedsItAndSearchesWithModelIdAndTopN() {
        EmbeddingModel embeddingModel = embeddingModel(7L, 1024);
        float[] queryEmbedding = embedding(1.0f);
        RetrievedChunk chunk = new RetrievedChunk(1L, 11L, "Java", "JVM", "content", 0.92);
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel);
        when(embeddingClient.embed("what is JVM?")).thenReturn(queryEmbedding);
        when(chunkRetrievalMapper.searchTopN(7L, queryEmbedding, 5)).thenReturn(List.of(chunk));

        List<RetrievedChunk> chunks = retrievalService.retrieveTopN("  what is JVM?  ", 5);

        assertThat(chunks).containsExactly(chunk);
        verify(embeddingClient).embed("what is JVM?");
        verify(embeddingModelResolver).resolveRequired1024Model();
        verify(chunkRetrievalMapper).searchTopN(eq(7L), same(queryEmbedding), eq(5));
    }

    @Test
    void retrieveTopNUsesConfiguredDefaultWhenTopNIsNotProvided() {
        EmbeddingModel embeddingModel = embeddingModel(7L, 1024);
        float[] queryEmbedding = embedding(1.0f);
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel);
        when(embeddingClient.embed("what is JVM?")).thenReturn(queryEmbedding);

        retrievalService.retrieveTopN("what is JVM?");

        verify(chunkRetrievalMapper).searchTopN(eq(7L), same(queryEmbedding), eq(20));
    }

    @Test
    void retrieveTopNFailsWhenQuestionIsBlank() {
        assertThatThrownBy(() -> retrievalService.retrieveTopN("   ", 5))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INVALID_REQUEST);
                    assertThat(exception).hasMessage("question must not be null or blank");
                });
        verifyNoInteractions(embeddingClient, embeddingModelResolver, chunkRetrievalMapper);
    }

    @Test
    void retrieveTopNFailsWhenTopNIsNotPositive() {
        assertThatThrownBy(() -> retrievalService.retrieveTopN("question", 0))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INVALID_REQUEST);
                    assertThat(exception).hasMessage("topN must be greater than 0");
                });
        verifyNoInteractions(embeddingClient, embeddingModelResolver, chunkRetrievalMapper);
    }

    @Test
    void retrieveTopNFailsWhenTopNExceedsLimit() {
        assertThatThrownBy(() -> retrievalService.retrieveTopN("question", 51))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INVALID_REQUEST);
                    assertThat(exception).hasMessage("topN must not be greater than 50");
                });
        verifyNoInteractions(embeddingClient, embeddingModelResolver, chunkRetrievalMapper);
    }

    @Test
    void retrieveTopNPropagatesResolverBusinessException() {
        BusinessException modelException = new BusinessException(
                CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                "model missing");
        when(embeddingModelResolver.resolveRequired1024Model()).thenThrow(modelException);

        assertThatThrownBy(() -> retrievalService.retrieveTopN("question", 5))
                .isSameAs(modelException);
        verifyNoInteractions(embeddingClient, chunkRetrievalMapper);
    }

    @Test
    void retrieveTopNFailsWhenQueryEmbeddingDimensionMismatchesModel() {
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel(7L, 1024));
        when(embeddingClient.embed("question")).thenReturn(new float[] {1.0f, 2.0f});

        assertThatThrownBy(() -> retrievalService.retrieveTopN("question", 5))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_RESULT_INVALID);
                    assertThat(exception).hasMessage("Query embedding dimension mismatch: expected=1024, actual=2");
                });
        verifyNoInteractions(chunkRetrievalMapper);
    }

    private static RetrievalProperties retrievalProperties(int defaultTopN, int maxTopN) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setDefaultTopN(defaultTopN);
        properties.setMaxTopN(maxTopN);
        return properties;
    }

    private static EmbeddingModel embeddingModel(Long id, int dimension) {
        EmbeddingModel model = new EmbeddingModel();
        model.setId(id);
        model.setDimension(dimension);
        return model;
    }

    private static float[] embedding(float firstValue) {
        float[] embedding = new float[1024];
        embedding[0] = firstValue;
        return embedding;
    }
}
