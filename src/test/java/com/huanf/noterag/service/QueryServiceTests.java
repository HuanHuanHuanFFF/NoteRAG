package com.huanf.noterag.service;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.client.NoopLlmClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.LlmProperties;
import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.CitationMarkers;
import com.huanf.noterag.rag.RagPrompt;
import com.huanf.noterag.rag.RagPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServiceTests {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final RerankService rerankService = mock(RerankService.class);
    private final RagPromptBuilder ragPromptBuilder = mock(RagPromptBuilder.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final QueryService queryService = new QueryService(
            retrievalService,
            rerankService,
            ragPromptBuilder,
            llmClient,
            llmProperties(true));

    @Test
    void queryRetrievesReranksBuildsPromptCallsLlmAndReturnsCitedSourcesOnly() {
        List<RetrievedChunk> retrievedChunks = List.of(
                chunk(111L, "Java", "JVM", "first", 0.91),
                chunk(222L, "MySQL", "Index", "second", 0.82));
        List<RetrievedChunk> rerankedChunks = List.of(
                chunk(222L, "MySQL", "Index", "second", 0.98),
                chunk(111L, "Java", "JVM", "first", 0.76));
        RagPrompt prompt = new RagPrompt("system", "user");
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(ragPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(prompt);
        String answer = "JVM answer" + CitationMarkers.format(111L)
                + " Index answer" + CitationMarkers.format(222L)
                + " JVM again" + CitationMarkers.format(111L);
        when(llmClient.chat(prompt)).thenReturn(answer);

        QueryResponse response = queryService.query("  what is index?  ");

        assertThat(response.getAnswer()).isEqualTo(answer);
        assertThat(response.getSources()).hasSize(2);
        assertThat(response.getSources()).extracting("chunkId").containsExactly(111L, 222L);
        assertThat(response.getSources()).extracting("score").containsExactly(0.76, 0.98);
        verify(retrievalService).retrieveTopN(eq("what is index?"));
        verify(rerankService).rerank(eq("what is index?"), same(retrievedChunks));
        verify(ragPromptBuilder).build(eq("what is index?"), same(rerankedChunks));
        verify(llmClient).chat(same(prompt));
    }

    @Test
    void queryReturnsRerankedSourcesWhenLlmDisabled() {
        RetrievalService localRetrievalService = mock(RetrievalService.class);
        RerankService localRerankService = mock(RerankService.class);
        RagPromptBuilder localPromptBuilder = mock(RagPromptBuilder.class);
        QueryService localQueryService = new QueryService(
                localRetrievalService,
                localRerankService,
                localPromptBuilder,
                new NoopLlmClient(),
                llmProperties(false));
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        when(localRetrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(localRerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(localPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(new RagPrompt("system", "user"));

        QueryResponse response = localQueryService.query(" what is index? ");

        assertThat(response.getAnswer()).isEmpty();
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources()).extracting("chunkId").containsExactly(1L);
        assertThat(response.getSources()).extracting("content").containsExactly("reranked");
        verify(localPromptBuilder).build(eq("what is index?"), same(rerankedChunks));
    }

    @Test
    void queryReturnsEmptySourcesWhenAnswerHasNoCitations() {
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        RagPrompt prompt = new RagPrompt("system", "user");
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(ragPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("根据当前笔记内容无法确定。");

        QueryResponse response = queryService.query(" what is index? ");

        assertThat(response.getAnswer()).isEqualTo("根据当前笔记内容无法确定。");
        assertThat(response.getSources()).isEmpty();
    }

    @Test
    void queryThrowsWhenAnswerReferencesMissingSourceId() {
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        RagPrompt prompt = new RagPrompt("system", "user");
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(ragPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("bad source" + CitationMarkers.format(99));

        assertThatThrownBy(() -> queryService.query(" what is index? "))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID);
                    assertThat(exception).hasMessage("LLM 返回了非法引用信息，请重试");
                });
    }

    @Test
    void queryThrowsWhenAnswerContainsMalformedCitation() {
        List<RetrievedChunk> retrievedChunks = List.of(chunk(1L, "MySQL", "Index", "retrieved", 0.91));
        List<RetrievedChunk> rerankedChunks = List.of(chunk(1L, "MySQL", "Index", "reranked", 0.88));
        RagPrompt prompt = new RagPrompt("system", "user");
        when(retrievalService.retrieveTopN("what is index?")).thenReturn(retrievedChunks);
        when(rerankService.rerank("what is index?", retrievedChunks)).thenReturn(rerankedChunks);
        when(ragPromptBuilder.build("what is index?", rerankedChunks)).thenReturn(prompt);
        String malformedCitation = CitationMarkers.START
                + CitationMarkers.TYPE
                + CitationMarkers.SEPARATOR
                + "s1"
                + CitationMarkers.END;
        when(llmClient.chat(prompt)).thenReturn("bad source" + malformedCitation);

        assertThatThrownBy(() -> queryService.query(" what is index? "))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID);
                    assertThat(exception).hasMessage("LLM 返回了非法引用信息，请重试");
                });
    }

    private static RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(100L + chunkId, chunkId, title, headingPath, content, score);
    }

    private static LlmProperties llmProperties(boolean enabled) {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(enabled);
        return properties;
    }
}
