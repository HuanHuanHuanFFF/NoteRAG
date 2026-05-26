package com.huanf.noterag.service;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
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
import com.huanf.noterag.entity.Note;
import com.huanf.noterag.entity.NoteChunk;
import com.huanf.noterag.model.NoteListItem;
import com.huanf.noterag.util.EstimatedTokenCounter;

/**
 * Note 导入与查询编排服务。
 */
@Slf4j
@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteChunkMapper noteChunkMapper;
    private final MarkdownChunkTransformer markdownChunkTransformer;
    private final NoteEmbeddingService noteEmbeddingService;
    private final TransactionTemplate transactionTemplate;

    public NoteService(
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
        log.info("Note 导入开始, titleLength={}, charCount={}, tokenCount={}", title.length(), charCount, tokenCount);

        SavedChunks savedChunks = transactionTemplate.execute(status ->
                saveNoteAndChunks(title, content, charCount, tokenCount));
        if (savedChunks == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Import transaction returned no result");
        }

        noteEmbeddingService.embedAndStore(title, savedChunks.chunks());

        log.info("Note 导入完成, noteId={}, chunkCount={}, charCount={}, tokenCount={}",
                savedChunks.noteId(), savedChunks.chunks().size(), charCount, tokenCount);
        return new ImportTextResponse(savedChunks.noteId(), savedChunks.chunks().size(), charCount, tokenCount);
    }

    /**
     * 查询前端左侧笔记列表所需的基础信息。
     */
    public List<NoteListItem> listNotes() {
        return noteMapper.findSummaries();
    }

    /**
     * 查询单篇笔记原文详情。
     */
    public Note getNoteDetail(Long noteId) {
        Note note = noteMapper.findById(noteId);
        if (note == null) {
            throw new BusinessException(CodeStatus.DOCUMENT_NOT_FOUND, "note not found");
        }
        return note;
    }

    /**
     * 在导入事务内保存 note 原文和切块结果。
     */
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

    /**
     * 校验批量插入后的 chunk 数量和主键回填结果。
     */
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

    /**
     * 从 chunk metadata 读取 long 类型字段。
     */
    private Long readLongMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Missing long metadata: " + key);
    }

    /**
     * 从 chunk metadata 读取 integer 类型字段。
     */
    private Integer readIntegerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Missing integer metadata: " + key);
    }

    /**
     * 规范化导入标题，并拒绝空标题。
     */
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

    /**
     * 规范化导入内容换行，并拒绝空白内容。
     */
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
