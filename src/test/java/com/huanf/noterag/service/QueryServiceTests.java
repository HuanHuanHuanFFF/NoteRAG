package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.model.RetrievedChunk;

class QueryServiceTests {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final QueryService queryService = new QueryService(retrievalService, rerankService);

    @Test
    void queryRetrievesReranksAndReturnsSourcesWithRerankScores() {
        List<RetrievedChunk> retrievedChunks = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82));
        List<RetrievedChunk> rerankedChunks = List.of(
                chunk(2L, "MySQL", "Index", "second", 0.98),
                chunk(1L, "Java", "JVM", "first", 0.76));
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);

        QueryResponse response = queryService.query("  what is index?  ");

        assertThat(response.getAnswer()).isEmpty();
        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources()).extracting("chunkId").containsExactly(2L, 1L);
        assertThat(response.getSources()).extracting("score").containsExactly(0.98, 0.76);
        verify(retrievalService).retrieveTopN(eq("what is index?"));
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
    }

    private static RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(100L + chunkId, chunkId, title, headingPath, content, score);
    }
}
