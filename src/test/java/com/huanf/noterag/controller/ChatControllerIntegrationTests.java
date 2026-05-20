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

import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.model.ChatResult;
import com.huanf.noterag.service.ChatService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:noterag-chat-controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class ChatControllerIntegrationTests {

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private ChatService chatService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sendFirstMessageWrapsChatResult() throws Exception {
        when(chatService.sendMessage(isNull(), eq("what is JVM?")))
                .thenReturn(new ChatResult(
                        1L,
                        "what is JVM?",
                        11L,
                        12L,
                        "answer",
                        List.of(new RetrievedChunk(2L, 21L, "Java Guide", "JVM > GC", "GC notes", 0.97))));

        mockMvc.perform(post("/api/chat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "what is JVM?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.sessionId").value(1))
                .andExpect(jsonPath("$.data.sessionTitle").value("what is JVM?"))
                .andExpect(jsonPath("$.data.userMessageId").value(11))
                .andExpect(jsonPath("$.data.assistantMessageId").value(12))
                .andExpect(jsonPath("$.data.answer").value("answer"))
                .andExpect(jsonPath("$.data.sources[0].noteId").value(2))
                .andExpect(jsonPath("$.data.sources[0].chunkId").value(21))
                .andExpect(jsonPath("$.data.sources[0].title").value("Java Guide"))
                .andExpect(jsonPath("$.data.sources[0].headingPath").value("JVM > GC"))
                .andExpect(jsonPath("$.data.sources[0].content").value("GC notes"))
                .andExpect(jsonPath("$.data.sources[0].score").value(0.97));

        verify(chatService).sendMessage(isNull(), eq("what is JVM?"));
    }

    @Test
    void sendMessageToExistingSessionWrapsChatResult() throws Exception {
        when(chatService.sendMessage(7L, "follow up"))
                .thenReturn(new ChatResult(7L, "title", 31L, 32L, "answer", List.of()));

        mockMvc.perform(post("/api/chat-sessions/7/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "follow up"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(7))
                .andExpect(jsonPath("$.data.userMessageId").value(31))
                .andExpect(jsonPath("$.data.assistantMessageId").value(32))
                .andExpect(jsonPath("$.data.answer").value("answer"))
                .andExpect(jsonPath("$.data.sources").isArray());

        verify(chatService).sendMessage(7L, "follow up");
    }

    @Test
    void sendMessageRejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/chat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
