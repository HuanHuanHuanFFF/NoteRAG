package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.mapper.NoteChunkMapper;
import com.huanf.noterag.model.NoteChunk;

@SpringBootTest
@TestPropertySource(properties = {
        "noterag.chunking.strategy=spring-ai-default",
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag_spring_ai_default;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class NoteImportServiceSpringAiDefaultIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private NoteEmbeddingService noteEmbeddingService;

    @Autowired
    private NoteImportService noteImportService;

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
    void importTextCanUseSpringAiDefaultChunkingStrategy() {
        String content = "# Java\n\n" + "Spring AI default baseline import text. ".repeat(200);

        ImportTextResponse response = noteImportService.importText(
                new ImportTextRequest("Spring AI Baseline", content));

        assertThat(response.getDocumentId()).isNotNull();
        assertThat(response.getChunkCount()).isPositive();

        List<NoteChunk> savedChunks = findChunksByNoteId(response.getDocumentId());
        assertThat(savedChunks).hasSize(response.getChunkCount());
        assertThat(savedChunks)
                .extracting(NoteChunk::getNoteId)
                .containsOnly(response.getDocumentId());
        assertThat(savedChunks)
                .extracting(NoteChunk::getChunkIndex)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.range(0, savedChunks.size()).boxed().toList());
        assertThat(savedChunks)
                .extracting(NoteChunk::getHeadingPath)
                .containsOnlyNulls();
        assertThat(savedChunks)
                .allSatisfy(chunk -> {
                    assertThat(chunk.getCharCount()).isPositive();
                    assertThat(chunk.getTokenCount()).isPositive();
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(noteEmbeddingService).embedAndStore(eq("Spring AI Baseline"), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(response.getChunkCount());
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
