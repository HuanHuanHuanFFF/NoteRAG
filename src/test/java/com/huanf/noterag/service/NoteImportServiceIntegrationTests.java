package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.NoteChunkMapper;
import com.huanf.noterag.mapper.NoteMapper;
import com.huanf.noterag.model.Note;
import com.huanf.noterag.model.NoteChunk;
import com.huanf.noterag.util.EstimatedTokenCounter;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none",
        "spring.ai.model.moderation=none"
})
class NoteImportServiceIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private NoteEmbeddingService noteEmbeddingService;

    @Autowired
    private NoteImportService noteImportService;

    @Autowired
    private NoteMapper noteMapper;

    @MockitoBean
    private NoteChunkMapper noteChunkMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpNoteChunkMapper() {
        when(noteChunkMapper.batchInsertReturning(any())).thenAnswer(invocation ->
                insertChunksReturning(invocation.getArgument(0)));
    }

    @Test
    void importTextPersistsDocumentAndChunks() {
        String rawContent = """
                # Java\r
                \r
                Java notes.\r
                \r
                ## Collections\r
                \r
                HashMap notes.\r
                """;
        String normalizedContent = rawContent.replace("\r\n", "\n").replace('\r', '\n');

        ImportTextResponse response = noteImportService.importText(new ImportTextRequest("  Java Guide  ", rawContent));

        assertThat(response.getDocumentId()).isNotNull();
        assertThat(response.getChunkCount()).isEqualTo(2);
        assertThat(response.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(response.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        Note savedNote = noteMapper.findById(response.getDocumentId());
        assertThat(savedNote).isNotNull();
        assertThat(savedNote.getTitle()).isEqualTo("Java Guide");
        assertThat(savedNote.getContent()).isEqualTo(normalizedContent);
        assertThat(savedNote.getCharCount()).isEqualTo(normalizedContent.length());
        assertThat(savedNote.getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate(normalizedContent));

        List<NoteChunk> savedChunks = findChunksByNoteId(response.getDocumentId());
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks)
                .extracting(NoteChunk::getNoteId)
                .containsOnly(response.getDocumentId());
        assertThat(savedChunks)
                .extracting(NoteChunk::getChunkIndex)
                .containsExactly(0, 1);
        assertThat(savedChunks)
                .extracting(NoteChunk::getHeadingPath)
                .containsExactly("Java", "Java > Collections");
        assertThat(savedChunks.get(0).getContent()).isEqualTo("Java notes.");
        assertThat(savedChunks.get(0).getCharCount()).isEqualTo("Java notes.".length());
        assertThat(savedChunks.get(0).getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate("Java notes."));
        assertThat(savedChunks.get(1).getContent()).isEqualTo("HashMap notes.");
        assertThat(savedChunks.get(1).getCharCount()).isEqualTo("HashMap notes.".length());
        assertThat(savedChunks.get(1).getTokenCount()).isEqualTo(EstimatedTokenCounter.estimate("HashMap notes."));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(noteEmbeddingService).embedAndStore(eq("Java Guide"), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(2);
        assertThat(chunksCaptor.getValue())
                .extracting(NoteChunk::getId)
                .doesNotContainNull();
    }

    @Test
    void importTextRejectsBlankTitleAfterNormalization() {
        assertThatThrownBy(() -> noteImportService.importText(new ImportTextRequest("   ", "# Java\n\nnotes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void importTextKeepsNoteAndChunksWhenEmbeddingFails() {
        BusinessException embeddingException = new BusinessException(
                CodeStatus.EMBEDDING_FAILED,
                "embedding failed");
        when(noteEmbeddingService.embedAndStore(any(), any())).thenThrow(embeddingException);

        assertThatThrownBy(() -> noteImportService.importText(new ImportTextRequest(
                "Embedding Failure",
                "# Java\n\nJava notes.")))
                .isSameAs(embeddingException);

        Long noteId = jdbcTemplate.queryForObject(
                "SELECT id FROM notes WHERE title = ?",
                Long.class,
                "Embedding Failure");
        assertThat(noteId).isNotNull();

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM note_chunks WHERE note_id = ?",
                Integer.class,
                noteId);
        assertThat(chunkCount).isEqualTo(1);
    }

    @Test
    void importTextRollsBackNoteWhenChunkInsertReturningDoesNotReturnSavedChunks() {
        doReturn(List.of()).when(noteChunkMapper).batchInsertReturning(any());

        assertThatThrownBy(() -> noteImportService.importText(new ImportTextRequest(
                "Broken Returning",
                "# Java\n\nJava notes.")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.CHUNK_METADATA_INVALID);
                    assertThat(exception).hasMessage("Saved chunk count mismatch after insert returning");
                });

        Integer noteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notes WHERE title = ?",
                Integer.class,
                "Broken Returning");
        assertThat(noteCount).isZero();
    }

    private List<NoteChunk> insertChunksReturning(List<NoteChunk> chunks) {
        for (NoteChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO note_chunks (note_id, chunk_index, heading_path, content, char_count, token_count)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    chunk.getNoteId(),
                    chunk.getChunkIndex(),
                    chunk.getHeadingPath(),
                    chunk.getContent(),
                    chunk.getCharCount(),
                    chunk.getTokenCount());
        }
        return findChunksByNoteId(chunks.get(0).getNoteId());
    }

    private List<NoteChunk> findChunksByNoteId(Long noteId) {
        return jdbcTemplate.query("""
                SELECT id,
                       note_id,
                       chunk_index,
                       heading_path,
                       content,
                       char_count,
                       token_count,
                       created_at
                FROM note_chunks
                WHERE note_id = ?
                ORDER BY chunk_index
                """, (rs, rowNum) -> {
            NoteChunk chunk = new NoteChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setNoteId(rs.getLong("note_id"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setHeadingPath(rs.getString("heading_path"));
            chunk.setContent(rs.getString("content"));
            chunk.setCharCount(rs.getInt("char_count"));
            chunk.setTokenCount(rs.getInt("token_count"));
            chunk.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            return chunk;
        }, noteId);
    }
}
