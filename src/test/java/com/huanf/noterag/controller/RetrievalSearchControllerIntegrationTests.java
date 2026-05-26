package com.huanf.noterag.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.service.RetrievalService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag-retrieval-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class RetrievalSearchControllerIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private RetrievalService retrievalService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchWrapsRetrievedSources() throws Exception {
        when(retrievalService.retrieveTopN("JVM GC 是什么?", 5, List.of(1L, 2L)))
                .thenReturn(List.of(new RetrievedChunk(
                        1L,
                        11L,
                        "Java Guide",
                        "JVM > GC",
                        "GC notes",
                        0.87)));

        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "JVM GC 是什么?",
                                  "topN": 5,
                                  "noteIds": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.sources[0].noteId").value(1))
                .andExpect(jsonPath("$.data.sources[0].chunkId").value(11))
                .andExpect(jsonPath("$.data.sources[0].title").value("Java Guide"))
                .andExpect(jsonPath("$.data.sources[0].headingPath").value("JVM > GC"))
                .andExpect(jsonPath("$.data.sources[0].content").value("GC notes"))
                .andExpect(jsonPath("$.data.sources[0].score").value(0.87));

        verify(retrievalService).retrieveTopN(eq("JVM GC 是什么?"), eq(5), eq(List.of(1L, 2L)));
    }

    @Test
    void searchUsesConfiguredDefaultTopNWhenRequestOmitsTopN() throws Exception {
        when(retrievalService.retrieveTopN("JVM GC 是什么?", null)).thenReturn(List.of());

        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "JVM GC 是什么?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources").isArray());

        verify(retrievalService).retrieveTopN(eq("JVM GC 是什么?"), isNull());
    }

    @Test
    void searchRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": " ",
                                  "topN": 5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void searchRejectsNonPositiveTopN() throws Exception {
        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "JVM GC 是什么?",
                                  "topN": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void searchRejectsTooManyNoteIds() throws Exception {
        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "JVM GC 是什么?",
                                  "topN": 5,
                                  "noteIds": [
                                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                                    11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                                    21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                                    31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                                    41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
                                    51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
                                    61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
                                    71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
                                    81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
                                    91, 92, 93, 94, 95, 96, 97, 98, 99, 100,
                                    101
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void searchWrapsBusinessException() throws Exception {
        when(retrievalService.retrieveTopN("JVM GC 是什么?", 99, null))
                .thenThrow(new BusinessException(CodeStatus.INVALID_REQUEST, "topN must not be greater than 50"));

        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "JVM GC 是什么?",
                                  "topN": 99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("topN must not be greater than 50"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
