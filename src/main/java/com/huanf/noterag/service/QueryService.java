package com.huanf.noterag.service;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.LlmProperties;
import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.AnswerCitationExtractor;
import com.huanf.noterag.rag.RagPrompt;
import com.huanf.noterag.rag.RagPromptBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QueryService {

    private static final String INVALID_CITATION_MESSAGE = "LLM 返回了非法引用信息，请重试";

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final RagPromptBuilder ragPromptBuilder;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;

    public QueryService(
            RetrievalService retrievalService,
            RerankService rerankService,
            RagPromptBuilder ragPromptBuilder,
            LlmClient llmClient,
            LlmProperties llmProperties
    ) {
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
        this.ragPromptBuilder = ragPromptBuilder;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
    }

    public QueryResponse query(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }
        String normalizedQuestion = question.strip();
        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveTopN(normalizedQuestion);
        List<RetrievedChunk> rerankedChunks = rerankService.rerank(normalizedQuestion, retrievedChunks);
        RagPrompt prompt = ragPromptBuilder.build(normalizedQuestion, rerankedChunks);
        String answer = llmClient.chat(prompt);
        List<SourceChunkResponse> sources = llmProperties.isEnabled()
                ? filterSourcesByAnswerCitations(answer, rerankedChunks)
                : allSources(rerankedChunks);
        return new QueryResponse(answer, sources);
    }

    private List<SourceChunkResponse> allSources(List<RetrievedChunk> rerankedChunks) {
        return rerankedChunks.stream()
                .map(SourceChunkResponse::from)
                .toList();
    }

    private List<SourceChunkResponse> filterSourcesByAnswerCitations(String answer, List<RetrievedChunk> rerankedChunks) {
        List<Long> citedSourceIds;
        try {
            citedSourceIds = AnswerCitationExtractor.extractSourceIds(answer);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, INVALID_CITATION_MESSAGE, ex);
        }
        Map<Long, RetrievedChunk> chunkById = rerankedChunks.stream()
                .collect(Collectors.toMap(
                        RetrievedChunk::getChunkId,
                        Function.identity(),
                        (first, ignored) -> first));
        return citedSourceIds.stream()
                .map(sourceId -> getSourceById(chunkById, sourceId))
                .toList();
    }

    private SourceChunkResponse getSourceById(Map<Long, RetrievedChunk> chunkById, long sourceId) {
        RetrievedChunk chunk = chunkById.get(sourceId);
        if (chunk == null) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, INVALID_CITATION_MESSAGE);
        }
        return SourceChunkResponse.from(chunk);
    }
}
