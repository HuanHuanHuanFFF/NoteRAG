package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.model.RetrievedChunk;

class QueryServiceTests {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final QueryService queryService = new QueryService(retrievalService, rerankService);

    @Test
    void querySourcesRetrievesAndReranks() {
        List<RetrievedChunk> retrievedChunks = List.of(
                chunk(111L, "Java", "JVM", "first", 0.91),
                chunk(222L, "MySQL", "Index", "second", 0.82));
        List<RetrievedChunk> rerankedChunks = List.of(
                chunk(222L, "MySQL", "Index", "second", 0.98),
                chunk(111L, "Java", "JVM", "first", 0.76));
        when(retrievalService.retrieveTopN("what is index?", null)).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);

        List<RetrievedChunk> response = queryService.querySources("  what is index?  ");

        assertThat(response).hasSize(2);
        assertThat(response).extracting(RetrievedChunk::getChunkId).containsExactly(222L, 111L);
        assertThat(response).extracting(RetrievedChunk::getScore).containsExactly(0.98, 0.76);
        verify(retrievalService).retrieveTopN(eq("what is index?"), isNull());
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
    }

    @Test
    void querySourcesRejectsBlankQuestion() {
        assertThatThrownBy(() -> queryService.querySources(" "))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INVALID_REQUEST);
                    assertThat(exception).hasMessage("question must not be null or blank");
                });
    }

    @Test
    void querySourcesPassesNormalizedQuestionToRetrievalAndRerank() {
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        when(retrievalService.retrieveTopN("what is index?", null)).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);

        List<RetrievedChunk> response = queryService.querySources(" what is index? ");

        assertThat(response).containsExactlyElementsOf(rerankedChunks);
        verify(retrievalService).retrieveTopN(eq("what is index?"), isNull());
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
    }

    @Test
    void querySourcesPassesNoteIdsToRetrievalOnly() {
        List<Long> noteIds = List.of(1L, 2L);
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        when(retrievalService.retrieveTopN("what is index?", noteIds)).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);

        List<RetrievedChunk> response = queryService.querySources(" what is index? ", noteIds);

        assertThat(response).containsExactlyElementsOf(rerankedChunks);
        verify(retrievalService).retrieveTopN(eq("what is index?"), same(noteIds));
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
    }

    private static RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(100L + chunkId, chunkId, title, headingPath, content, score);
    }
}
