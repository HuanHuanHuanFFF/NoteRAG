package com.huanf.noterag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import com.huanf.noterag.model.NoteChunk;
import com.huanf.noterag.service.NoteEmbeddingService;

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
class NoteImportControllerIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private NoteEmbeddingService noteEmbeddingService;

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
                .andExpect(jsonPath("$.data.chunkCount").value(1))
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
        }, chunks.get(0).getNoteId());
    }
}
