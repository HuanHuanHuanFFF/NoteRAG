package com.huanf.noterag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.huanf.noterag.mapper.NoteChunkMapper;
import com.huanf.noterag.entity.NoteChunk;
import com.huanf.noterag.entity.NoteChunkType;
import com.huanf.noterag.service.NoteEmbeddingService;
import com.huanf.noterag.service.NoteSummaryService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class NoteControllerIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private NoteEmbeddingService noteEmbeddingService;

    @MockitoBean
    private NoteSummaryService noteSummaryService;

    @MockitoBean
    private NoteChunkMapper noteChunkMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpNoteChunkMapper() {
        when(noteChunkMapper.batchInsertReturning(any())).thenAnswer(invocation ->
                insertChunksReturning(invocation.getArgument(0)));
        when(noteSummaryService.generateSummary(any(), any())).thenReturn("Java Guide 全文摘要");
    }

    @Test
    void importTextWrapsSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/note-imports/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Java Guide",
                                  "content": "# Java\\n\\nJava notes."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.documentId").isNumber())
                .andExpect(jsonPath("$.data.chunkCount").value(2))
                .andExpect(jsonPath("$.data.charCount").isNumber())
                .andExpect(jsonPath("$.data.tokenCount").isNumber());
    }

    @Test
    void importTextWrapsValidationFailure() throws Exception {
        mockMvc.perform(post("/api/note-imports/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "content": "# Java\\n\\nJava notes."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void listNotesReturnsBasicNoteInfoWithChunkCount() throws Exception {
        insertNote(1001L, "Read API", "# Read API", 10, 5);
        insertChunk(1001L, 0, "Intro", "first chunk", 11, 6);
        insertChunk(1001L, 1, "Detail", "second chunk", 12, 7);

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.notes[?(@.id == 1001)].title").value(Matchers.contains("Read API")))
                .andExpect(jsonPath("$.data.notes[?(@.id == 1001)].charCount").value(Matchers.contains(10)))
                .andExpect(jsonPath("$.data.notes[?(@.id == 1001)].tokenCount").value(Matchers.contains(5)))
                .andExpect(jsonPath("$.data.notes[?(@.id == 1001)].chunkCount").value(Matchers.contains(2)))
                .andExpect(jsonPath("$.data.notes[?(@.id == 1001)].createdAt").isNotEmpty());
    }

    @Test
    void getNoteDetailReturnsOriginalContent() throws Exception {
        insertNote(1002L, "Detail API", "# Detail API\n\ncontent", 21, 9);

        mockMvc.perform(get("/api/notes/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1002))
                .andExpect(jsonPath("$.data.title").value("Detail API"))
                .andExpect(jsonPath("$.data.content").value("# Detail API\n\ncontent"))
                .andExpect(jsonPath("$.data.charCount").value(21))
                .andExpect(jsonPath("$.data.tokenCount").value(9))
                .andExpect(jsonPath("$.data.createdAt").isString());
    }

    @Test
    void archiveNoteReturnsEmptyDataAndHidesArchivedNote() throws Exception {
        insertNote(1003L, "Archive API", "# Archive API", 13, 6);
        insertChunk(1003L, 0, "Archive API", "chunk", 5, 2);

        mockMvc.perform(delete("/api/notes/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        mockMvc.perform(get("/api/notes/1003"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes[?(@.id == 1003)]").isEmpty());
    }

    @Test
    void archiveNoteTreatsArchivedNoteAsNotFound() throws Exception {
        insertNote(1004L, "Archive Twice API", "# Archive Twice", 15, 7);
        mockMvc.perform(delete("/api/notes/1004"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notes/1004"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void missingApiPathReturnsUnifiedNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void unsupportedImportTextMethodReturnsUnifiedMethodNotAllowedResponse() throws Exception {
        mockMvc.perform(get("/api/note-imports/text"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500))
                .andExpect(jsonPath("$.message").value("请求方法不支持"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void unsupportedImportTextContentTypeReturnsUnifiedUnsupportedMediaTypeResponse() throws Exception {
        mockMvc.perform(post("/api/note-imports/text")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("# Java\n\nJava notes."))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41500))
                .andExpect(jsonPath("$.message").value("请求媒体类型不支持"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void unsupportedImportTextAcceptReturnsUnifiedNotAcceptableResponse() throws Exception {
        mockMvc.perform(post("/api/note-imports/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("""
                                {
                                  "title": "Java Guide",
                                  "content": "# Java\\n\\nJava notes."
                                }
                                """))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value(40600))
                .andExpect(jsonPath("$.message").value("响应媒体类型不支持"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void healthResponseIsNotWrapped() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("NoteRAG is running"));
    }

    private List<NoteChunk> insertChunksReturning(List<NoteChunk> chunks) {
        for (NoteChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO note_chunks (note_id, chunk_type, chunk_index, heading_path, content, char_count, token_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    chunk.getNoteId(),
                    chunk.getChunkType().name(),
                    chunk.getChunkIndex(),
                    chunk.getHeadingPath(),
                    chunk.getContent(),
                    chunk.getCharCount(),
                    chunk.getTokenCount());
        }
        return jdbcTemplate.query("""
                SELECT id,
                       note_id,
                       chunk_type,
                       chunk_index,
                       heading_path,
                       content,
                       char_count,
                       token_count,
                       created_at
                FROM note_chunks
                WHERE note_id = ?
                ORDER BY chunk_type, chunk_index
                """, (rs, rowNum) -> {
            NoteChunk chunk = new NoteChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setNoteId(rs.getLong("note_id"));
            chunk.setChunkType(NoteChunkType.valueOf(rs.getString("chunk_type")));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setHeadingPath(rs.getString("heading_path"));
            chunk.setContent(rs.getString("content"));
            chunk.setCharCount(rs.getInt("char_count"));
            chunk.setTokenCount(rs.getInt("token_count"));
            chunk.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            return chunk;
                }, chunks.get(0).getNoteId());
    }

    private void insertNote(Long id, String title, String content, int charCount, int tokenCount) {
        jdbcTemplate.update("""
                INSERT INTO notes (id, title, content, char_count, token_count)
                VALUES (?, ?, ?, ?, ?)
                """, id, title, content, charCount, tokenCount);
    }

    private void insertChunk(Long noteId, int chunkIndex, String headingPath, String content, int charCount, int tokenCount) {
        jdbcTemplate.update("""
                INSERT INTO note_chunks (note_id, chunk_type, chunk_index, heading_path, content, char_count, token_count)
                VALUES (?, 'CONTENT', ?, ?, ?, ?, ?)
                """, noteId, chunkIndex, headingPath, content, charCount, tokenCount);
    }
}
