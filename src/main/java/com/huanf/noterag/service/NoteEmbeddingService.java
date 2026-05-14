package com.huanf.noterag.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.mapper.ChunkEmbedding1024Mapper;
import com.huanf.noterag.model.ChunkEmbedding1024;
import com.huanf.noterag.model.EmbeddingModel;
import com.huanf.noterag.model.NoteChunk;
import com.huanf.noterag.util.EmbeddingTextFormatter;

/**
 * Note chunk 向量化编排服务。
 *
 * <p>该服务只处理“已入库 chunk -> 调用 EmbeddingClient -> 校验结果 -> 写入向量表”的流程，
 * 不负责 Markdown 导入、chunk 生成或查询检索。外部 embedding API 调用必须保持在数据库写入事务之外，
 * 避免远程调用期间长时间占用数据库连接。</p>
 */
@Service
public class NoteEmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelResolver embeddingModelResolver;
    private final ChunkEmbedding1024Mapper chunkEmbedding1024Mapper;
    private final TransactionTemplate transactionTemplate;

    public NoteEmbeddingService(
            EmbeddingClient embeddingClient,
            EmbeddingModelResolver embeddingModelResolver,
            ChunkEmbedding1024Mapper chunkEmbedding1024Mapper,
            TransactionTemplate transactionTemplate
    ) {
        this.embeddingClient = embeddingClient;
        this.embeddingModelResolver = embeddingModelResolver;
        this.chunkEmbedding1024Mapper = chunkEmbedding1024Mapper;
        this.transactionTemplate = transactionTemplate;
    }

    public int embedAndStore(String title, List<NoteChunk> chunks) {
        validateChunks(chunks);
        if (chunks.isEmpty()) {
            return 0;
        }

        EmbeddingModel embeddingModel = embeddingModelResolver.resolveRequired1024Model();

        List<String> embeddingTexts = chunks.stream()
                .map(chunk -> EmbeddingTextFormatter.formatChunkForEmbedding(
                        title,
                        chunk.getHeadingPath(),
                        chunk.getContent()))
                .toList();
        List<float[]> embeddings = embeddingClient.embedAll(embeddingTexts);
        validateEmbeddingResults(embeddings, chunks.size(), embeddingModel.getDimension());

        Integer inserted = transactionTemplate.execute(status -> insertEmbeddings(chunks, embeddings, embeddingModel.getId()));
        return inserted == null ? 0 : inserted;
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
    private void validateEmbeddingResults(List<float[]> embeddings, int expectedSize, int expectedDimension) {
        if (embeddings == null || embeddings.size() != expectedSize) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Embedding result count mismatch: expected=%d, actual=%s"
                            .formatted(expectedSize, embeddings == null ? "null" : embeddings.size()));
        }

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
