package com.huanf.noterag.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RetrievalProperties;
import com.huanf.noterag.mapper.ChunkRetrievalMapper;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.util.ChunkContextFormatter;

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

        EmbeddingModel embeddingModel = embeddingModelResolver.resolveRequired1024Model();

        String queryText = ChunkContextFormatter.formatQueryForEmbedding(question);
        float[] queryEmbedding = embeddingClient.embed(queryText);
        validateQueryEmbedding(queryEmbedding, embeddingModel.getDimension());

        return chunkRetrievalMapper.searchTopN(embeddingModel.getId(), queryEmbedding, topN);
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
