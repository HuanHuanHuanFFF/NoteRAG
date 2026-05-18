package com.huanf.noterag.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.prompt.RagPrompt;
import com.huanf.noterag.prompt.RagPromptBuilder;

@Service
public class QueryService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;
    private final RagPromptBuilder ragPromptBuilder;
    private final LlmClient llmClient;

    public QueryService(
            RetrievalService retrievalService,
            RerankService rerankService,
            RagPromptBuilder ragPromptBuilder,
            LlmClient llmClient
    ) {
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
        this.ragPromptBuilder = ragPromptBuilder;
        this.llmClient = llmClient;
    }

    public QueryResponse query(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }
        String normalizedQuestion = question.strip();
        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveTopN(normalizedQuestion);
        List<RetrievedChunk> rerankedChunks = rerankService.rerank(normalizedQuestion, retrievedChunks);
        List<SourceChunkResponse> sources = rerankedChunks.stream()
                .map(SourceChunkResponse::from)
                .toList();
        RagPrompt prompt = ragPromptBuilder.build(normalizedQuestion, rerankedChunks);
        String answer = llmClient.chat(prompt);
        return new QueryResponse(answer, sources);
    }
}
