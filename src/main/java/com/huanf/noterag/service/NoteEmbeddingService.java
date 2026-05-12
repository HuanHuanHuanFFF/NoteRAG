package com.huanf.noterag.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.EmbeddingProperties;
import com.huanf.noterag.mapper.ChunkEmbedding1024Mapper;
import com.huanf.noterag.mapper.EmbeddingModelMapper;
import com.huanf.noterag.model.ChunkEmbedding1024;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.NoteChunk;

/**
 * Note chunk 向量化编排服务。
 *
 * <p>该服务只处理“已入库 chunk -> 调用 EmbeddingClient -> 校验结果 -> 写入向量表”的流程，
 * 不负责 Markdown 导入、chunk 生成或查询检索。外部 embedding API 调用必须保持在数据库写入事务之外，
 * 避免远程调用期间长时间占用数据库连接。</p>
 */
@Service
public class NoteEmbeddingService {

    private static final int SUPPORTED_DIMENSION_1024 = 1024;

    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelMapper embeddingModelMapper;
    private final ChunkEmbedding1024Mapper chunkEmbedding1024Mapper;
    private final TransactionTemplate transactionTemplate;

    public NoteEmbeddingService(
            EmbeddingProperties embeddingProperties,
            EmbeddingClient embeddingClient,
            EmbeddingModelMapper embeddingModelMapper,
            ChunkEmbedding1024Mapper chunkEmbedding1024Mapper,
            TransactionTemplate transactionTemplate
    ) {
        this.embeddingProperties = embeddingProperties;
        this.embeddingClient = embeddingClient;
        this.embeddingModelMapper = embeddingModelMapper;
        this.chunkEmbedding1024Mapper = chunkEmbedding1024Mapper;
        this.transactionTemplate = transactionTemplate;
    }

    public int embedAndStore(List<NoteChunk> chunks) {
        validateChunks(chunks);
        if (chunks.isEmpty()) {
            return 0;
        }

        if (!embeddingProperties.isEnabled()) {
            throw new BusinessException(CodeStatus.EMBEDDING_CONFIG_INVALID,
                    "Embedding is disabled. Set noterag.embedding.enabled=true before embedding chunks.");
        }

        validateEmbeddingConfig();

        if (!Integer.valueOf(SUPPORTED_DIMENSION_1024).equals(embeddingProperties.getDimension())) {
            throw new BusinessException(CodeStatus.EMBEDDING_DIMENSION_UNSUPPORTED,
                    "Only 1024-dimension embeddings can be stored currently, configured dimension=%d"
                            .formatted(embeddingProperties.getDimension()));
        }

        EmbeddingModel embeddingModel = embeddingModelMapper.findEnabledBySpec(
                embeddingProperties.getProvider(),
                embeddingProperties.getModelName(),
                embeddingProperties.getDimension(),
                embeddingProperties.getDistanceMetric());
        if (embeddingModel == null) {
            throw new BusinessException(CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                    "Enabled embedding model config not found: provider=%s, modelName=%s, dimension=%d, distanceMetric=%s"
                            .formatted(
                                    embeddingProperties.getProvider(),
                                    embeddingProperties.getModelName(),
                                    embeddingProperties.getDimension(),
                                    embeddingProperties.getDistanceMetric()));
        }
        validateEmbeddingModel(embeddingModel);

        List<String> contents = chunks.stream()
                .map(NoteChunk::getContent)
                .toList();
        List<float[]> embeddings = embeddingClient.embedAll(contents);
        validateEmbeddingResults(embeddings, chunks.size());

        Integer inserted = transactionTemplate.execute(status -> insertEmbeddings(chunks, embeddings, embeddingModel.getId()));
        return inserted == null ? 0 : inserted;
    }

    /**
     * 校验 embedding 功能配置。
     *
     * <p>应用可以在 embedding 关闭或未完整配置时启动；但真正执行向量化时，
     * provider、modelName、distanceMetric 和 dimension 必须完整有效。</p>
     */
    private void validateEmbeddingConfig() {
        if (isBlank(embeddingProperties.getProvider())
                || isBlank(embeddingProperties.getModelName())
                || isBlank(embeddingProperties.getDistanceMetric())
                || embeddingProperties.getDimension() == null
                || embeddingProperties.getDimension() <= 0) {
            throw new BusinessException(CodeStatus.EMBEDDING_CONFIG_INVALID,
                    "Embedding config is invalid: provider, modelName, distanceMetric must be set and dimension must be greater than 0");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 在短事务中写入 chunk embedding。
     *
     * <p>调用方已经完成远程 embedding API 调用和结果校验，这里只做数据库 insert。</p>
     */
    private int insertEmbeddings(List<NoteChunk> chunks, List<float[]> embeddings, Long embeddingModelId) {
        int inserted = 0;
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEmbedding1024 chunkEmbedding = new ChunkEmbedding1024();
            chunkEmbedding.setNoteChunkId(chunks.get(i).getId());
            chunkEmbedding.setEmbeddingModelId(embeddingModelId);
            chunkEmbedding.setEmbedding(embeddings.get(i));
            inserted += chunkEmbedding1024Mapper.insert(chunkEmbedding);
        }
        return inserted;
    }

    /**
     * 校验数据库中的 embedding model 配置与当前运行配置一致。
     */
    private void validateEmbeddingModel(EmbeddingModel embeddingModel) {
        if (embeddingModel.getId() == null) {
            throw new BusinessException(CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                    "Embedding model config id must not be null");
        }
        if (embeddingModel.getDimension() == null
                || !embeddingModel.getDimension().equals(embeddingProperties.getDimension())) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Embedding model dimension mismatch: expected=%d, actual=%s"
                            .formatted(embeddingProperties.getDimension(), embeddingModel.getDimension()));
        }
    }

    /**
     * 校验传入 chunks 已经持久化，并且具备可向量化内容。
     */
    private void validateChunks(List<NoteChunk> chunks) {
        if (chunks == null) {
            throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID, "chunks must not be null");
        }
        for (int i = 0; i < chunks.size(); i++) {
            NoteChunk chunk = chunks.get(i);
            if (chunk == null) {
                throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID,
                        "chunk[%d] must not be null".formatted(i));
            }
            if (chunk.getId() == null) {
                throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID,
                        "chunk[%d].id must not be null before embedding".formatted(i));
            }
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID,
                        "chunk[%d].content must not be null or blank before embedding".formatted(i));
            }
        }
    }

    /**
     * 校验模型返回结果与输入 chunk 一一对应，且维度符合当前配置。
     */
    private void validateEmbeddingResults(List<float[]> embeddings, int expectedSize) {
        if (embeddings == null || embeddings.size() != expectedSize) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Embedding result count mismatch: expected=%d, actual=%s"
                            .formatted(expectedSize, embeddings == null ? "null" : embeddings.size()));
        }

        int expectedDimension = embeddingProperties.getDimension();
        for (int i = 0; i < embeddings.size(); i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null || embedding.length != expectedDimension) {
                throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                        "Embedding dimension mismatch at index %d: expected=%d, actual=%s"
                                .formatted(i, expectedDimension, embedding == null ? "null" : embedding.length));
            }
        }
    }
}
