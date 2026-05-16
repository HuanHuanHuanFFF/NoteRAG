package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.huanf.noterag.client.RerankClient;
import com.huanf.noterag.client.RerankResult;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RerankProperties;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.util.EmbeddingTextFormatter;

class RerankServiceTests {

    private final RerankClient rerankClient = mock(RerankClient.class);

    @Test
    void rerankFallsBackToRetrievalOrderAndDefaultTopKWhenDisabled() {
        RerankProperties properties = rerankProperties(false, 2, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82),
                chunk(3L, "Redis", "Cache", "third", 0.73));

        List<RetrievedChunk> results = service.rerank("question", candidates);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(RetrievedChunk::getChunkId).containsExactly(1L, 2L);
        assertThat(results).extracting(RetrievedChunk::getScore).containsExactly(0.91, 0.82);
        assertThat(results.get(0)).isNotSameAs(candidates.get(0));
        verifyNoInteractions(rerankClient);
    }

    @Test
    void rerankFallsBackToRetrievalOrderAndExplicitTopKWhenDisabled() {
        RerankProperties properties = rerankProperties(false, 1, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82),
                chunk(3L, "Redis", "Cache", "third", 0.73));

        List<RetrievedChunk> results = service.rerank("question", candidates, 3);

        assertThat(results).extracting(RetrievedChunk::getChunkId).containsExactly(1L, 2L, 3L);
        verifyNoInteractions(rerankClient);
    }

    @Test
    void rerankRejectsTopKOutOfRange() {
        RerankService service = new RerankService(rerankClient, rerankProperties(false, 2, 5, 10));
        List<RetrievedChunk> candidates = List.of(chunk(1L, "Java", "JVM", "first", 0.91));

        assertThatThrownBy(() -> service.rerank("question", candidates, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK must be greater than 0");
        assertThatThrownBy(() -> service.rerank("question", candidates, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK must not be greater than 5");
        verifyNoInteractions(rerankClient);
    }

    @Test
    void rerankRejectsCandidatesOverMaxDocuments() {
        RerankService service = new RerankService(rerankClient, rerankProperties(true, 2, 5, 1));
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82));

        assertThatThrownBy(() -> service.rerank("question", candidates, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_CONFIG_INVALID);
                    assertThat(exception)
                            .hasMessage("rerank maxDocuments must cover candidate size: candidates=2, maxDocuments=1");
                });
        verifyNoInteractions(rerankClient);
    }

    @Test
    void rerankCallsClientWithFormattedDocumentsAndAppliesReturnedOrderAndScores() {
        RerankProperties properties = rerankProperties(true, 3, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82),
                chunk(3L, "Redis", "Cache", "third", 0.73));
        when(rerankClient.rerank(
                eq("how to use index?"),
                org.mockito.ArgumentMatchers.anyList(),
                eq(2),
                eq(properties.getInstruct())))
                .thenReturn(List.of(new RerankResult(2, 0.99), new RerankResult(0, 0.88)));

        List<RetrievedChunk> results = service.rerank("  how to use index?  ", candidates, 2);

        assertThat(results).extracting(RetrievedChunk::getChunkId).containsExactly(3L, 1L);
        assertThat(results).extracting(RetrievedChunk::getScore).containsExactly(0.99, 0.88);
        assertThat(candidates).extracting(RetrievedChunk::getScore).containsExactly(0.91, 0.82, 0.73);
        assertThat(results.get(0)).isNotSameAs(candidates.get(2));

        ArgumentCaptor<List<String>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(rerankClient).rerank(
                eq("how to use index?"),
                documentsCaptor.capture(),
                eq(2),
                eq(properties.getInstruct()));
        assertThat(documentsCaptor.getValue()).containsExactly(
                EmbeddingTextFormatter.formatChunkForEmbedding("Java", "JVM", "first"),
                EmbeddingTextFormatter.formatChunkForEmbedding("MySQL", "Index", "second"),
                EmbeddingTextFormatter.formatChunkForEmbedding("Redis", "Cache", "third"));
    }

    @Test
    void rerankRejectsInvalidResultIndex() {
        RerankProperties properties = rerankProperties(true, 2, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(chunk(1L, "Java", "JVM", "first", 0.91));
        when(rerankClient.rerank(
                eq("question"),
                org.mockito.ArgumentMatchers.anyList(),
                eq(1),
                eq(properties.getInstruct())))
                .thenReturn(List.of(new RerankResult(1, 0.99)));

        assertThatThrownBy(() -> service.rerank("question", candidates, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_RESULT_INVALID);
                    assertThat(exception).hasMessage("Rerank result index out of range: 1");
                });
    }

    @Test
    void rerankRejectsDuplicateResultIndex() {
        RerankProperties properties = rerankProperties(true, 2, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82));
        when(rerankClient.rerank(
                eq("question"),
                org.mockito.ArgumentMatchers.anyList(),
                eq(2),
                eq(properties.getInstruct())))
                .thenReturn(List.of(new RerankResult(0, 0.99), new RerankResult(0, 0.88)));

        assertThatThrownBy(() -> service.rerank("question", candidates, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_RESULT_INVALID);
                    assertThat(exception).hasMessage("Rerank result index duplicated: 0");
                });
    }

    @Test
    void rerankRejectsResultsOverRequestedTopK() {
        RerankProperties properties = rerankProperties(true, 2, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82));
        when(rerankClient.rerank(
                eq("question"),
                org.mockito.ArgumentMatchers.anyList(),
                eq(1),
                eq(properties.getInstruct())))
                .thenReturn(List.of(new RerankResult(0, 0.99), new RerankResult(1, 0.88)));

        assertThatThrownBy(() -> service.rerank("question", candidates, 1))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_RESULT_INVALID);
                    assertThat(exception).hasMessage("Rerank results size must not be greater than 1");
                });
    }

    @Test
    void rerankRejectsEmptyClientResults() {
        RerankProperties properties = rerankProperties(true, 2, 5, 10);
        RerankService service = new RerankService(rerankClient, properties);
        List<RetrievedChunk> candidates = List.of(chunk(1L, "Java", "JVM", "first", 0.91));
        when(rerankClient.rerank(
                eq("question"),
                org.mockito.ArgumentMatchers.anyList(),
                eq(1),
                eq(properties.getInstruct())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.rerank("question", candidates, 2))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_RESULT_INVALID);
                    assertThat(exception).hasMessage("Rerank results must not be empty");
                });
    }

    private static RerankProperties rerankProperties(boolean enabled, int defaultTopK, int maxTopK, int maxDocuments) {
        RerankProperties properties = new RerankProperties();
        properties.setEnabled(enabled);
        properties.setDefaultTopK(defaultTopK);
        properties.setMaxTopK(maxTopK);
        properties.setMaxDocuments(maxDocuments);
        properties.setInstruct("rerank instruction");
        return properties;
    }

    private static RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(100L + chunkId, chunkId, title, headingPath, content, score);
    }
}
