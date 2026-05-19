package com.huanf.noterag.service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RetrievalProperties;
import com.huanf.noterag.mapper.ChunkRetrievalMapper;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.util.RagTextFormatter;

@Slf4j
@Service
public class RetrievalService {

    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelResolver embeddingModelResolver;
    private final ChunkRetrievalMapper chunkRetrievalMapper;
    private final RetrievalProperties retrievalProperties;

    public RetrievalService(
            EmbeddingClient embeddingClient,
            EmbeddingModelResolver embeddingModelResolver,
            ChunkRetrievalMapper chunkRetrievalMapper,
            RetrievalProperties retrievalProperties
    ) {
        this.embeddingClient = embeddingClient;
        this.embeddingModelResolver = embeddingModelResolver;
        this.chunkRetrievalMapper = chunkRetrievalMapper;
        this.retrievalProperties = retrievalProperties;
    }

    public List<RetrievedChunk> retrieveTopN(String question) {
        return retrieveTopN(question, retrievalProperties.getDefaultTopN());
    }

    public List<RetrievedChunk> retrieveTopN(String question, int topN) {
        validateQuestion(question);
        validateTopN(topN);

        log.info("Retrieval 开始, questionLength={}, topN={}", question.length(), topN);
        log.debug("Retrieval question={}", question);
        long startNanos = System.nanoTime();

        EmbeddingModel embeddingModel = embeddingModelResolver.resolveRequired1024Model();

        String queryText = RagTextFormatter.formatQueryText(question);
        float[] queryEmbedding = embeddingClient.embed(queryText);
        validateQueryEmbedding(queryEmbedding, embeddingModel.getDimension());

        List<RetrievedChunk> results = chunkRetrievalMapper.searchTopN(embeddingModel.getId(), queryEmbedding, topN);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Retrieval 完成, returnedCount={}, elapsedMs={}", results.size(), elapsedMs);
        if (log.isDebugEnabled()) {
            log.debug("Retrieval 命中 chunkIds/scores={}",
                    results.stream()
                            .map(c -> c.getChunkId() + ":" + String.format("%.4f", c.getScore()))
                            .toList());
        }
        return results;
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }
    }

    private void validateTopN(int topN) {
        if (topN <= 0) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "topN must be greater than 0");
        }
        if (topN > retrievalProperties.getMaxTopN()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST,
                    "topN must not be greater than %d".formatted(retrievalProperties.getMaxTopN()));
        }
    }

    private void validateQueryEmbedding(float[] queryEmbedding, int expectedDimension) {
        if (queryEmbedding == null || queryEmbedding.length != expectedDimension) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Query embedding dimension mismatch: expected=%d, actual=%s"
                            .formatted(expectedDimension,
                                    queryEmbedding == null ? "null" : queryEmbedding.length));
        }
    }
}
