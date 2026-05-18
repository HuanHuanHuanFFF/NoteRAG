package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.client.NoopLlmClient;
import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.prompt.RagPrompt;
import com.huanf.noterag.prompt.RagPromptBuilder;

class QueryServiceTests {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagPromptBuilder ragPromptBuilder = mock(RagPromptBuilder.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final QueryService queryService = new QueryService(
            retrievalService,
            rerankService,
            ragPromptBuilder,
            llmClient);

    @Test
    void queryRetrievesReranksBuildsPromptCallsLlmAndReturnsAnswerWithSources() {
        List<RetrievedChunk> retrievedChunks = List.of(
                chunk(1L, "Java", "JVM", "first", 0.91),
                chunk(2L, "MySQL", "Index", "second", 0.82));
        List<RetrievedChunk> rerankedChunks = List.of(
                chunk(2L, "MySQL", "Index", "second", 0.98),
                chunk(1L, "Java", "JVM", "first", 0.76));
        RagPrompt prompt = new RagPrompt("system", "user");
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(ragPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("Index answer");

        QueryResponse response = queryService.query("  what is index?  ");

        assertThat(response.getAnswer()).isEqualTo("Index answer");
        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources()).extracting("chunkId").containsExactly(2L, 1L);
        assertThat(response.getSources()).extracting("score").containsExactly(0.98, 0.76);
        verify(retrievalService).retrieveTopN(eq("what is index?"));
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
        verify(ragPromptBuilder).build(eq("what is index?"), same(rerankedChunks));
        verify(llmClient).chat(same(prompt));
    }

    @Test
    void queryReturnsEmptyAnswerWithSourcesWhenUsingNoopLlmClient() {
        RetrievalService localRetrievalService = mock(RetrievalService.class);
        RerankService localRerankService = mock(RerankService.class);
        RagPromptBuilder localPromptBuilder = mock(RagPromptBuilder.class);
        QueryService localQueryService = new QueryService(
                localRetrievalService,
                localRerankService,
                localPromptBuilder,
                new NoopLlmClient());
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        when(localRetrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(localRerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(localPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(new RagPrompt("system", "user"));

        QueryResponse response = localQueryService.query(" what is index? ");

        assertThat(response.getAnswer()).isEmpty();
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources()).extracting("chunkId").containsExactly(1L);
        assertThat(response.getSources()).extracting("score").containsExactly(0.88);
        verify(localPromptBuilder).build(eq("what is index?"), same(rerankedChunks));
    }

    private static RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(100L + chunkId, chunkId, title, headingPath, content, score);
    }
}
