package com.huanf.noterag.service;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.model.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class QueryService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;

    public QueryService(RetrievalService retrievalService, RerankService rerankService) {
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
    }

    /**
     * 执行单轮 query sources 调试链路，只返回 rerank 后的候选片段。
     */
    public List<RetrievedChunk> querySources(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }

        String normalizedQuestion = question.strip();
        log.info("Query sources 开始, questionLength={}", normalizedQuestion.length());
        log.debug("Query sources question={}", normalizedQuestion);
        long startNanos = System.nanoTime();

        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveTopN(normalizedQuestion);
        List<RetrievedChunk> rerankedChunks = rerankService.rerank(normalizedQuestion, retrievedChunks);

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Query sources 完成, retrievedCount={}, rerankedCount={}, elapsedMs={}",
                retrievedChunks.size(), rerankedChunks.size(), elapsedMs);
        return rerankedChunks;
    }
}
