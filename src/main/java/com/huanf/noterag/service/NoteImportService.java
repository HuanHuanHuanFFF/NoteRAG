package com.huanf.noterag.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.huanf.noterag.chunk.MarkdownChunkTransformer;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.NoteChunkMapper;
import com.huanf.noterag.mapper.NoteMapper;
import com.huanf.noterag.model.Note;
import com.huanf.noterag.model.NoteChunk;
import com.huanf.noterag.util.EstimatedTokenCounter;

@Service
public class NoteImportService {

    private final NoteMapper noteMapper;
    private final NoteChunkMapper noteChunkMapper;
    private final MarkdownChunkTransformer markdownChunkTransformer;
    private final NoteEmbeddingService noteEmbeddingService;
    private final TransactionTemplate transactionTemplate;

    public NoteImportService(
            NoteMapper noteMapper,
            NoteChunkMapper noteChunkMapper,
            MarkdownChunkTransformer markdownChunkTransformer,
            NoteEmbeddingService noteEmbeddingService,
            TransactionTemplate transactionTemplate
    ) {
        this.noteMapper = noteMapper;
        this.noteChunkMapper = noteChunkMapper;
        this.markdownChunkTransformer = markdownChunkTransformer;
        this.noteEmbeddingService = noteEmbeddingService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 导入 Markdown 原文。
     *
     * <p>流程约束固定为：先入库 document，拿到持久化 ID，再把该 ID 放入 source metadata 后执行 chunk。
     * 这样后续即使扩展为批处理或异步 chunk，chunk 结果也仍然可以通过 metadata 回溯到源 document。</p>
     */
    public ImportTextResponse importText(ImportTextRequest request) {
        String title = normalizeTitle(request.getTitle());
        String content = normalizeContent(request.getContent());
        int charCount = content.length();
        int tokenCount = EstimatedTokenCounter.estimate(content);

        SavedChunks savedChunks = transactionTemplate.execute(status ->
                saveNoteAndChunks(title, content, charCount, tokenCount));
        if (savedChunks == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Import transaction returned no result");
        }

        noteEmbeddingService.embedAndStore(title, savedChunks.chunks());

        return new ImportTextResponse(savedChunks.noteId(), savedChunks.chunks().size(), charCount, tokenCount);
    }

    private SavedChunks saveNoteAndChunks(String title, String content, int charCount, int tokenCount) {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setCharCount(charCount);
        note.setTokenCount(tokenCount);
        noteMapper.insert(note);

        List<Document> chunkDocuments = markdownChunkTransformer.transform(
                List.of(new Document(
                        content,
                        Map.of(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY, note.getId()))
                ));

        List<NoteChunk> chunks = chunkDocuments
                .stream()
                .map(this::toDocumentChunk)
                .toList();

        if (chunks.isEmpty()) {
            throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID, "Markdown chunker returned no chunks");
        }

        List<NoteChunk> savedChunks = noteChunkMapper.batchInsertReturning(chunks);
        validateSavedChunks(chunks, savedChunks);
        return new SavedChunks(note.getId(), savedChunks);
    }

    private void validateSavedChunks(List<NoteChunk> chunks, List<NoteChunk> savedChunks) {
        if (savedChunks == null || savedChunks.size() != chunks.size()) {
            throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID,
                    "Saved chunk count mismatch after insert returning");
        }
        for (int i = 0; i < savedChunks.size(); i++) {
            if (savedChunks.get(i).getId() == null) {
                throw new BusinessException(CodeStatus.CHUNK_METADATA_INVALID,
                        "Saved chunk[%d].id must not be null after insert returning".formatted(i));
            }
        }
    }

    /**
     * 将 Spring AI chunk Document 转成数据库实体。
     *
     * <p>documentId 统一从 chunk metadata 读取，而不是由外层额外传参，
     * 这样可以保持 chunk 归属关系跟随 chunk 一起流转。</p>
     */
    private NoteChunk toDocumentChunk(Document chunk) {
        NoteChunk noteChunk = new NoteChunk();
        noteChunk.setNoteId(readLongMetadata(chunk.getMetadata(), MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY));
        noteChunk.setChunkIndex(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.CHUNK_INDEX_METADATA_KEY));
        noteChunk.setHeadingPath((String) chunk.getMetadata().get(MarkdownChunkTransformer.HEADING_PATH_METADATA_KEY));
        noteChunk.setContent(chunk.getText());
        noteChunk.setCharCount(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY));
        noteChunk.setTokenCount(readIntegerMetadata(chunk.getMetadata(), MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY));
        return noteChunk;
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

    private record SavedChunks(Long noteId, List<NoteChunk> chunks) {
    }
}
