package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.mapper.ChunkEmbedding1024Mapper;
import com.huanf.noterag.model.ChunkEmbedding1024;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.NoteChunk;

class NoteEmbeddingServiceTests {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final EmbeddingModelResolver embeddingModelResolver = mock(EmbeddingModelResolver.class);
    private final ChunkEmbedding1024Mapper chunkEmbedding1024Mapper = mock(ChunkEmbedding1024Mapper.class);
    private final TransactionTemplate transactionTemplate = transactionTemplate();
    private final NoteEmbeddingService noteEmbeddingService = new NoteEmbeddingService(
            embeddingClient,
            embeddingModelResolver,
            chunkEmbedding1024Mapper,
            transactionTemplate);

    @Test
    void embedAndStoreWritesChunkEmbeddingsWithModelAndVector() {
        NoteChunk firstChunk = noteChunk(11L, "first", "Java > Collections");
        NoteChunk secondChunk = noteChunk(12L, "second", null);
        EmbeddingModel embeddingModel = embeddingModel(7L, 1024);
        float[] firstEmbedding = embedding(1.0f);
        float[] secondEmbedding = embedding(2.0f);
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel);
        when(embeddingClient.embedAll(any()))
                .thenReturn(List.of(firstEmbedding, secondEmbedding));
        when(chunkEmbedding1024Mapper.insert(any(ChunkEmbedding1024.class))).thenReturn(1);

        int inserted = noteEmbeddingService.embedAndStore("Java Guide", List.of(firstChunk, secondChunk));

        assertThat(inserted).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> embeddingTextsCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingClient).embedAll(embeddingTextsCaptor.capture());
        assertThat(embeddingTextsCaptor.getValue()).containsExactly(
                """
                        文档标题: Java Guide
                        章节路径: Java > Collections

                        正文:
                        first""",
                """
                        文档标题: Java Guide

                        正文:
                        second""");
        assertThat(embeddingTextsCaptor.getValue().get(1))
                .doesNotContain("章节路径:")
                .doesNotContain("null");

        ArgumentCaptor<ChunkEmbedding1024> captor = ArgumentCaptor.forClass(ChunkEmbedding1024.class);
        verify(chunkEmbedding1024Mapper, times(2)).insert(captor.capture());
        List<ChunkEmbedding1024> storedEmbeddings = captor.getAllValues();
        assertThat(storedEmbeddings).hasSize(2);
        assertThat(storedEmbeddings.get(0).getNoteChunkId()).isEqualTo(11L);
        assertThat(storedEmbeddings.get(0).getEmbeddingModelId()).isEqualTo(7L);
        assertThat(storedEmbeddings.get(0).getEmbedding()).isSameAs(firstEmbedding);
        assertThat(storedEmbeddings.get(1).getNoteChunkId()).isEqualTo(12L);
        assertThat(storedEmbeddings.get(1).getEmbeddingModelId()).isEqualTo(7L);
        assertThat(storedEmbeddings.get(1).getEmbedding()).isSameAs(secondEmbedding);
    }

    @Test
    void embedAndStoreReturnsZeroForEmptyChunks() {
        int inserted = noteEmbeddingService.embedAndStore("Java Guide", List.of());

        assertThat(inserted).isZero();
        verifyNoInteractions(embeddingClient, embeddingModelResolver, chunkEmbedding1024Mapper);
    }

    @Test
    void embedAndStorePropagatesResolverBusinessException() {
        BusinessException modelException = new BusinessException(
                CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                "model missing");
        when(embeddingModelResolver.resolveRequired1024Model()).thenThrow(modelException);

        assertThatThrownBy(() -> noteEmbeddingService.embedAndStore("Java Guide", List.of(noteChunk(11L, "content"))))
                .isSameAs(modelException);
        verifyNoInteractions(embeddingClient, chunkEmbedding1024Mapper);
    }

    @Test
    void embedAndStoreFailsWhenChunkHasNoId() {
        assertThatThrownBy(() -> noteEmbeddingService.embedAndStore("Java Guide", List.of(noteChunk(null, "content"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.CHUNK_METADATA_INVALID);
                    assertThat(exception).hasMessage("chunk[0].id must not be null before embedding");
                });
        verifyNoInteractions(embeddingClient, embeddingModelResolver, chunkEmbedding1024Mapper);
    }

    @Test
    void embedAndStoreFailsWhenEmbeddingResultCountMismatch() {
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel(7L, 1024));
        when(embeddingClient.embedAll(any()))
                .thenReturn(List.of(embedding(1.0f)));

        assertThatThrownBy(() -> noteEmbeddingService.embedAndStore("Java Guide",
                List.of(noteChunk(11L, "first"), noteChunk(12L, "second"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_RESULT_INVALID);
                    assertThat(exception).hasMessageContaining("Embedding result count mismatch");
                });
        verifyNoInteractions(chunkEmbedding1024Mapper);
    }

    @Test
    void embedAndStoreFailsWhenEmbeddingDimensionMismatch() {
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel(7L, 1024));
        when(embeddingClient.embedAll(any()))
                .thenReturn(List.of(new float[] {1.0f, 2.0f}));

        assertThatThrownBy(() -> noteEmbeddingService.embedAndStore("Java Guide", List.of(noteChunk(11L, "content"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_RESULT_INVALID);
                    assertThat(exception).hasMessage("Embedding dimension mismatch at index 0: expected=1024, actual=2");
                });
        verifyNoInteractions(chunkEmbedding1024Mapper);
    }

    @Test
    void embedAndStorePropagatesEmbeddingClientBusinessException() {
        BusinessException embeddingException = new BusinessException(
                CodeStatus.EMBEDDING_FAILED,
                "Embedding 服务调用失败");
        when(embeddingModelResolver.resolveRequired1024Model()).thenReturn(embeddingModel(7L, 1024));
        when(embeddingClient.embedAll(any())).thenThrow(embeddingException);

        assertThatThrownBy(() -> noteEmbeddingService.embedAndStore("Java Guide", List.of(noteChunk(11L, "content"))))
                .isSameAs(embeddingException);
        verifyNoInteractions(chunkEmbedding1024Mapper);
    }

    private static NoteChunk noteChunk(Long id, String content) {
        return noteChunk(id, content, null);
    }

    private static NoteChunk noteChunk(Long id, String content, String headingPath) {
        NoteChunk chunk = new NoteChunk();
        chunk.setId(id);
        chunk.setHeadingPath(headingPath);
        chunk.setContent(content);
        return chunk;
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

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        });
    }
}
