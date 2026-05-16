package com.huanf.noterag.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.huanf.noterag.client.RerankClient;
import com.huanf.noterag.client.RerankResult;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.RerankProperties;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.util.ChunkContextFormatter;

@Service
public class RerankService {

    private final RerankClient rerankClient;
    private final RerankProperties rerankProperties;

    public RerankService(RerankClient rerankClient, RerankProperties rerankProperties) {
        this.rerankClient = rerankClient;
        this.rerankProperties = rerankProperties;
    }

    /**
     * 使用当前配置的默认 topK 执行 rerank。
     *
     * <p>默认值来自 {@code noterag.rerank.default-top-k}</p>
     */
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates) {
        return rerank(question, candidates, rerankProperties.getDefaultTopK());
    }

    /**
     * 使用调用方显式指定的 topK 执行 rerank。
     */
    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates, int topK) {
        String normalizedQuestion = normalizeQuestion(question);
        validateTopK(topK);

        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() > rerankProperties.getMaxDocuments()) {
            throw new BusinessException(CodeStatus.RERANK_CONFIG_INVALID,
                    "rerank maxDocuments must cover candidate size: candidates=%d, maxDocuments=%d"
                            .formatted(candidates.size(), rerankProperties.getMaxDocuments()));
        }

        if (!rerankProperties.isEnabled()) {
            return copyTopCandidates(candidates, topK);
        }

        List<String> documents = candidates.stream()
                .map(candidate -> ChunkContextFormatter.formatChunkForEmbedding(
                        candidate.getTitle(),
                        candidate.getHeadingPath(),
                        candidate.getContent()))
                .toList();
        List<RerankResult> results = rerankClient.rerank(
                normalizedQuestion,
                documents,
                Math.min(topK, candidates.size()),
                rerankProperties.getInstruct());

        return applyResults(candidates, results, Math.min(topK, candidates.size()));
    }

    private void validateTopK(int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        if (topK > rerankProperties.getMaxTopK()) {
            throw new IllegalArgumentException(
                    "topK must not be greater than %d".formatted(rerankProperties.getMaxTopK()));
        }
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(CodeStatus.INVALID_REQUEST, "question must not be null or blank");
        }
        return question.strip();
    }

    private List<RetrievedChunk> copyTopCandidates(List<RetrievedChunk> candidates, int topK) {
        int limit = Math.min(topK, candidates.size());
        List<RetrievedChunk> chunks = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            chunks.add(copyChunk(candidates.get(i), candidates.get(i).getScore()));
        }
        return chunks;
    }

    private List<RetrievedChunk> applyResults(List<RetrievedChunk> candidates, List<RerankResult> results, int expectedMaxResults) {
        if (results == null || results.isEmpty()) {
            throw new BusinessException(CodeStatus.RERANK_RESULT_INVALID, "Rerank results must not be empty");
        }
        if (results.size() > expectedMaxResults) {
            throw new BusinessException(CodeStatus.RERANK_RESULT_INVALID,
                    "Rerank results size must not be greater than %d".formatted(expectedMaxResults));
        }

        Set<Integer> seenIndexes = new HashSet<>();
        List<RetrievedChunk> rerankedChunks = new ArrayList<>(results.size());
        // Rerank 服务返回顺序就是最终排序；本地只按 index 映射回候选 chunk，不再按 score 重新排序。
        for (RerankResult result : results) {
            int index = result.index();
            if (index < 0 || index >= candidates.size()) {
                throw new BusinessException(CodeStatus.RERANK_RESULT_INVALID,
                        "Rerank result index out of range: %d".formatted(index));
            }
            if (!seenIndexes.add(index)) {
                throw new BusinessException(CodeStatus.RERANK_RESULT_INVALID,
                        "Rerank result index duplicated: %d".formatted(index));
            }
            rerankedChunks.add(copyChunk(candidates.get(index), result.score()));
        }
        return rerankedChunks;
    }

    private RetrievedChunk copyChunk(RetrievedChunk source, Double score) {
        return new RetrievedChunk(
                source.getNoteId(),
                source.getChunkId(),
                source.getTitle(),
                source.getHeadingPath(),
                source.getContent(),
                score);
    }
}
