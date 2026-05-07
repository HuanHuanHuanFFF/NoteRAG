package com.huanf.noterag.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huanf.noterag.chunk.MarkdownChunkTransformer;
import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.DocumentChunkMapper;
import com.huanf.noterag.mapper.DocumentMapper;
import com.huanf.noterag.model.Document;
import com.huanf.noterag.util.EstimatedTokenCounter;

@Service
public class DocumentImportService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final MarkdownChunkTransformer markdownChunkTransformer;

    public DocumentImportService(
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            MarkdownChunkTransformer markdownChunkTransformer
    ) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.markdownChunkTransformer = markdownChunkTransformer;
    }

    /**
     * 导入 Markdown 原文。
     *
     * <p>流程约束固定为：先入库 document，拿到持久化 ID，再把该 ID 放入 source metadata 后执行 chunk。
     * 这样后续即使扩展为批处理或异步 chunk，chunk 结果也仍然可以通过 metadata 回溯到源 document。</p>
     */
    @Transactional
    public ImportTextResponse importText(ImportTextRequest request) {
        String title = normalizeTitle(request.getTitle());
        String content = normalizeContent(request.getContent());
        int charCount = content.length();
        int tokenCount = EstimatedTokenCounter.estimate(content);

        Document document = new Document();
        document.setTitle(title);
        document.setContent(content);
        document.setCharCount(charCount);
        document.setTokenCount(tokenCount);
        documentMapper.insert(document);

        List<org.springframework.ai.document.Document> chunkDocuments = markdownChunkTransformer.transform(
                List.of(new org.springframework.ai.document.Document(
                        content,
                        Map.of(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY, document.getId()))));

        List<com.huanf.noterag.model.DocumentChunk> chunks = chunkDocuments
                .stream()
                .map(this::toDocumentChunk)
                .toList();

        if (!chunks.isEmpty()) {
            documentChunkMapper.batchInsert(chunks);
        }

        return new ImportTextResponse(document.getId(), chunks.size(), charCount, tokenCount);
    }

    /**
     * 将 Spring AI chunk Document 转成数据库实体。
     *
     * <p>documentId 统一从 chunk metadata 读取，而不是由外层额外传参，
     * 这样可以保持 chunk 归属关系跟随 chunk 一起流转。</p>
     */
    private com.huanf.noterag.model.DocumentChunk toDocumentChunk(org.springframework.ai.document.Document chunk) {
        com.huanf.noterag.model.DocumentChunk documentChunk = new com.huanf.noterag.model.DocumentChunk();
        documentChunk.setDocumentId(readLongMetadata(chunk.getMetadata(), MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY));
        documentChunk.setChunkIndex(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.CHUNK_INDEX_METADATA_KEY));
        documentChunk.setHeadingPath((String) chunk.getMetadata().get(MarkdownChunkTransformer.HEADING_PATH_METADATA_KEY));
        documentChunk.setContent(chunk.getText());
        documentChunk.setCharCount(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY));
        documentChunk.setTokenCount(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY));
        return documentChunk;
    }

    private Long readLongMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Missing long metadata: " + key);
    }

    private Integer readIntegerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Missing integer metadata: " + key);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("title must not be null");
        }
        String normalized = title.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return normalized;
    }
}
