package com.huanf.noterag.service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RetrievalProperties;
import com.huanf.noterag.mapper.ChunkRetrievalMapper;
import com.huanf.noterag.entity.EmbeddingModel;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.RagTextFormatter;

/**
 * 向量检索服务，负责把用户问题转换成 query embedding，并从 pgvector 中召回 TopN chunk。
 *
 * <p>这里是 note scope 的统一入口：Controller/Query/Chat 只负责透传 noteIds，
 * 本服务负责清理非法 ID、控制 scope 大小，并把最终范围交给 SQL 层过滤。</p>
 */
@Slf4j
@Service
public class RetrievalService {

    /**
     * 单次检索最多允许限定的笔记数量，避免动态 IN 条件过长。
     */
    static final int MAX_NOTE_SCOPE_SIZE = 100;

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
        return retrieveTopN(question, retrievalProperties.getDefaultTopN(), null);
    }

    /**
     * 使用默认 TopN，并限定在指定笔记范围内检索。
     */
    public List<RetrievedChunk> retrieveTopN(String question, List<Long> noteIds) {
        return retrieveTopN(question, retrievalProperties.getDefaultTopN(), noteIds);
    }

    /**
     * 使用指定 TopN 进行全库检索。
     */
    public List<RetrievedChunk> retrieveTopN(String question, int topN) {
        return retrieveTopN(question, topN, null);
    }

    /**
     * 执行完整检索链路：校验参数、生成 query embedding、按可选 note scope 查询 TopN chunk。
     */
    public List<RetrievedChunk> retrieveTopN(String question, int topN, List<Long> noteIds) {
        validateQuestion(question);
        validateTopN(topN);
        List<Long> normalizedNoteIds = normalizeNoteIds(noteIds);

        log.info("Retrieval 开始, questionLength={}, topN={}, noteScopeCount={}",
                question.length(), topN, normalizedNoteIds == null ? 0 : normalizedNoteIds.size());
        log.debug("Retrieval question={}", question);
        long startNanos = System.nanoTime();

        EmbeddingModel embeddingModel = embeddingModelResolver.resolveRequired1024Model();

        String queryText = RagTextFormatter.formatQueryText(question);
        float[] queryEmbedding = embeddingClient.embed(queryText);
        validateQueryEmbedding(queryEmbedding, embeddingModel.getDimension());

        List<RetrievedChunk> results = chunkRetrievalMapper.searchTopN(
                embeddingModel.getId(),
                queryEmbedding,
                topN,
                normalizedNoteIds);
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

    /**
     * 规范化 note scope：null/空/清理后为空表示全库检索；有效 ID 会去重并保留原顺序。
     */
    private List<Long> normalizeNoteIds(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return null;
        }
        if (noteIds.size() > MAX_NOTE_SCOPE_SIZE) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST,
                    "noteIds size must not be greater than %d".formatted(MAX_NOTE_SCOPE_SIZE));
        }
        List<Long> normalized = noteIds.stream()
                .filter(noteId -> noteId != null && noteId > 0)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 校验检索问题，避免空问题进入 embedding 调用。
     */
    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }
    }

    /**
     * 校验 TopN，防止请求超出配置允许的检索规模。
     */
    private void validateTopN(int topN) {
        if (topN <= 0) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "topN must be greater than 0");
        }
        if (topN > retrievalProperties.getMaxTopN()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST,
                    "topN must not be greater than %d".formatted(retrievalProperties.getMaxTopN()));
        }
    }

    /**
     * 校验 query embedding 维度，避免错误向量进入 pgvector 查询。
     */
    private void validateQueryEmbedding(float[] queryEmbedding, int expectedDimension) {
        if (queryEmbedding == null || queryEmbedding.length != expectedDimension) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Query embedding dimension mismatch: expected=%d, actual=%s"
                            .formatted(expectedDimension,
                                    queryEmbedding == null ? "null" : queryEmbedding.length));
        }
    }
}
