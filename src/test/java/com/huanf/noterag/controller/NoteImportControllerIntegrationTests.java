package com.huanf.noterag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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

    @Autowired
    private MockMvc mockMvc;

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
}
