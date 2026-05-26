package com.huanf.noterag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.huanf.noterag.entity.ChatMessage;
import com.huanf.noterag.entity.ChatMessageRole;
import com.huanf.noterag.entity.ChatMessageStatus;
import com.huanf.noterag.entity.ChatSession;
import com.huanf.noterag.entity.ChatSessionStatus;
import com.huanf.noterag.mapper.ChatMessageMapper;
import com.huanf.noterag.mapper.ChatMessageSourceMapper;
import com.huanf.noterag.mapper.ChatSessionMapper;
import com.huanf.noterag.model.ChatMessageSourceChunk;
import com.huanf.noterag.model.ChatMessageWithSources;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatMessageSourceMapper chatMessageSourceMapper;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM chat_message_sources");
        jdbcTemplate.update("DELETE FROM chat_messages");
        jdbcTemplate.update("DELETE FROM chat_sessions");
        jdbcTemplate.update("DELETE FROM chunk_embeddings_1024");
        jdbcTemplate.update("DELETE FROM note_chunks");
        jdbcTemplate.update("DELETE FROM notes");
    }

    @Test
    void sendFirstMessageWrapsChatResult() throws Exception {
        when(chatService.sendMessage(isNull(), eq("what is JVM?"), eq(List.of(1L, 2L))))
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
                                  "content": "what is JVM?",
                                  "noteIds": [1, 2]
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

        verify(chatService).sendMessage(isNull(), eq("what is JVM?"), eq(List.of(1L, 2L)));
    }

    @Test
    void sendMessageToExistingSessionWrapsChatResult() throws Exception {
        when(chatService.sendMessage(eq(7L), eq("follow up"), isNull()))
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

        verify(chatService).sendMessage(eq(7L), eq("follow up"), isNull());
    }

    @Test
    void listSessionsWrapsSessionList() throws Exception {
        Instant createdAt = Instant.parse("2026-05-21T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-21T10:01:00Z");
        Instant lastMessageAt = Instant.parse("2026-05-21T10:02:00Z");
        when(chatService.listSessions()).thenReturn(List.of(
                new ChatSession(7L, "MySQL MVCC", ChatSessionStatus.ACTIVE, createdAt, updatedAt, lastMessageAt)));

        mockMvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessions[0].id").value(7))
                .andExpect(jsonPath("$.data.sessions[0].title").value("MySQL MVCC"))
                .andExpect(jsonPath("$.data.sessions[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sessions[0].createdAt").isString())
                .andExpect(jsonPath("$.data.sessions[0].updatedAt").isString())
                .andExpect(jsonPath("$.data.sessions[0].lastMessageAt").isString());
    }

    @Test
    void listMessagesWrapsMessagesWithAssistantSources() throws Exception {
        ChatMessage userMessage = new ChatMessage(
                31L,
                7L,
                ChatMessageRole.USER,
                "什么是 MVCC?",
                ChatMessageStatus.COMPLETED,
                null,
                9,
                Instant.parse("2026-05-21T10:00:00Z"),
                Instant.parse("2026-05-21T10:00:00Z"));
        ChatMessage assistantMessage = new ChatMessage(
                32L,
                7L,
                ChatMessageRole.ASSISTANT,
                "MVCC answer",
                ChatMessageStatus.COMPLETED,
                null,
                11,
                Instant.parse("2026-05-21T10:01:00Z"),
                Instant.parse("2026-05-21T10:01:00Z"));
        when(chatService.listMessages(7L)).thenReturn(List.of(
                new ChatMessageWithSources(userMessage, List.of()),
                new ChatMessageWithSources(assistantMessage, List.of(
                        new RetrievedChunk(2L, 21L, "MySQL", "Tx > MVCC", "undo log", 0.91)))));

        mockMvc.perform(get("/api/chat-sessions/7/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.messages[0].id").value(31))
                .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[0].content").value("什么是 MVCC?"))
                .andExpect(jsonPath("$.data.messages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.messages[0].errorCode").value(nullValue()))
                .andExpect(jsonPath("$.data.messages[0].sources").isArray())
                .andExpect(jsonPath("$.data.messages[1].id").value(32))
                .andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.messages[1].sources[0].noteId").value(2))
                .andExpect(jsonPath("$.data.messages[1].sources[0].chunkId").value(21))
                .andExpect(jsonPath("$.data.messages[1].sources[0].title").value("MySQL"))
                .andExpect(jsonPath("$.data.messages[1].sources[0].headingPath").value("Tx > MVCC"))
                .andExpect(jsonPath("$.data.messages[1].sources[0].content").value("undo log"))
                .andExpect(jsonPath("$.data.messages[1].sources[0].score").value(0.91));
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

    @Test
    void sendMessageRejectsTooManyNoteIds() throws Exception {
        mockMvc.perform(post("/api/chat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "what is JVM?",
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
    void chatSessionMapperFindAllReturnsLatestSessionsFirst() {
        insertChatSession(100L, "older", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T10:00:00Z", "2026-05-21T10:00:00Z");
        insertChatSession(101L, "same-last-older-updated", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T10:00:00Z", "2026-05-21T12:00:00Z");
        insertChatSession(102L, "same-last-same-updated-lower-id", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T11:00:00Z", "2026-05-21T12:00:00Z");
        insertChatSession(103L, "same-last-same-updated-higher-id", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T11:00:00Z", "2026-05-21T12:00:00Z");

        List<ChatSession> sessions = chatSessionMapper.findAll();

        assertThat(sessions)
                .extracting(ChatSession::getId)
                .containsExactly(103L, 102L, 101L, 100L);
    }

    @Test
    void chatMessageMapperFindBySessionIdReturnsMessagesInCreatedAtIdOrder() {
        insertChatSession(200L, "history", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z");
        insertChatMessage(202L, 200L, "USER", "later", "COMPLETED", null, 5,
                "2026-05-21T10:02:00Z", "2026-05-21T10:02:00Z");
        insertChatMessage(201L, 200L, "USER", "first-same-time", "COMPLETED", null, 15,
                "2026-05-21T10:01:00Z", "2026-05-21T10:01:00Z");
        insertChatMessage(203L, 200L, "ASSISTANT", "second-same-time", "COMPLETED", null, 16,
                "2026-05-21T10:01:00Z", "2026-05-21T10:01:00Z");

        List<ChatMessage> messages = chatMessageMapper.findBySessionId(200L);

        assertThat(messages)
                .extracting(ChatMessage::getId)
                .containsExactly(201L, 203L, 202L);
    }

    @Test
    void chatMessageSourceMapperFindSourceChunksJoinsNotesAndChunksInSourceOrder() {
        insertNote(300L, "MySQL", "# MySQL", 7, 7,
                "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z");
        insertNoteChunk(301L, 300L, 0, "Tx > MVCC", "undo log", 8, 8,
                "2026-05-21T09:01:00Z");
        insertNoteChunk(302L, 300L, 1, "Tx > Lock", "next-key lock", 13, 13,
                "2026-05-21T09:02:00Z");
        insertChatSession(4000L, "chat", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z");
        insertChatMessage(400L, 4000L, "ASSISTANT", "answer one", "COMPLETED", null, 10,
                "2026-05-21T10:00:00Z", "2026-05-21T10:00:00Z");
        insertChatMessage(401L, 4000L, "ASSISTANT", "answer two", "COMPLETED", null, 10,
                "2026-05-21T10:01:00Z", "2026-05-21T10:01:00Z");
        insertChatMessageSource(500L, 400L, 302L, 2, 0.82, "2026-05-21T10:00:01Z");
        insertChatMessageSource(501L, 400L, 301L, 1, 0.91, "2026-05-21T10:00:02Z");
        insertChatMessageSource(502L, 401L, 302L, 1, 0.50, "2026-05-21T10:01:01Z");

        List<ChatMessageSourceChunk> sources = chatMessageSourceMapper.findSourceChunksByMessageIds(List.of(400L, 401L));

        assertThat(sources).hasSize(3);
        assertThat(sources)
                .extracting(ChatMessageSourceChunk::getMessageId)
                .containsExactly(400L, 400L, 401L);
        assertThat(sources)
                .extracting(ChatMessageSourceChunk::getChunkId)
                .containsExactly(301L, 302L, 302L);
        assertThat(sources.get(0).getNoteId()).isEqualTo(300L);
        assertThat(sources.get(0).getTitle()).isEqualTo("MySQL");
        assertThat(sources.get(0).getHeadingPath()).isEqualTo("Tx > MVCC");
        assertThat(sources.get(0).getContent()).isEqualTo("undo log");
        assertThat(sources.get(0).getScore()).isEqualTo(0.91);
        assertThat(sources.get(1).getScore()).isEqualTo(0.82);
        assertThat(sources.get(2).getScore()).isEqualTo(0.50);
    }

    @Test
    void chatServiceListMessagesLoadsSourcesForAssistantMessagesOnlyUsingRealSql() {
        insertNote(600L, "MySQL", "# MySQL", 7, 7,
                "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z");
        insertNoteChunk(601L, 600L, 0, "Tx > MVCC", "user-side source", 16, 16,
                "2026-05-21T09:01:00Z");
        insertNoteChunk(602L, 600L, 1, "Tx > Lock", "assistant source", 16, 16,
                "2026-05-21T09:02:00Z");
        insertChatSession(5000L, "chat", "ACTIVE",
                "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z", "2026-05-21T09:00:00Z");
        insertChatMessage(5100L, 5000L, "USER", "question", "COMPLETED", null, 8,
                "2026-05-21T10:00:00Z", "2026-05-21T10:00:00Z");
        insertChatMessage(5101L, 5000L, "ASSISTANT", "answer", "COMPLETED", null, 6,
                "2026-05-21T10:01:00Z", "2026-05-21T10:01:00Z");
        insertChatMessageSource(6100L, 5100L, 601L, 1, 0.10, "2026-05-21T10:00:01Z");
        insertChatMessageSource(6101L, 5101L, 602L, 1, 0.90, "2026-05-21T10:01:01Z");
        ChatService readService = new ChatService(
                chatSessionMapper,
                chatMessageMapper,
                chatMessageSourceMapper,
                null,
                null,
                null,
                null,
                null);

        List<ChatMessageWithSources> messages = readService.listMessages(5000L);

        assertThat(messages).hasSize(2);
        assertThat(messages)
                .extracting(message -> message.getMessage().getId())
                .containsExactly(5100L, 5101L);
        assertThat(messages.get(0).getSources()).isEmpty();
        assertThat(messages.get(1).getSources())
                .extracting(RetrievedChunk::getChunkId)
                .containsExactly(602L);
        assertThat(messages.get(1).getSources().get(0).getTitle()).isEqualTo("MySQL");
        assertThat(messages.get(1).getSources().get(0).getScore()).isEqualTo(0.90);
    }

    private void insertChatSession(
            Long id,
            String title,
            String status,
            String createdAt,
            String updatedAt,
            String lastMessageAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO chat_sessions (id, title, status, created_at, updated_at, last_message_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id, title, status, timestamp(createdAt), timestamp(updatedAt), timestamp(lastMessageAt));
    }

    private void insertChatMessage(
            Long id,
            Long sessionId,
            String role,
            String content,
            String status,
            String errorCode,
            int charCount,
            String createdAt,
            String updatedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO chat_messages
                            (id, session_id, role, content, status, error_code, char_count, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, sessionId, role, content, status, errorCode, charCount, timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertNote(
            Long id,
            String title,
            String content,
            int charCount,
            int tokenCount,
            String createdAt,
            String updatedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO notes (id, title, content, char_count, token_count, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id, title, content, charCount, tokenCount, timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertNoteChunk(
            Long id,
            Long noteId,
            int chunkIndex,
            String headingPath,
            String content,
            int charCount,
            int tokenCount,
            String createdAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO note_chunks
                            (id, note_id, chunk_index, heading_path, content, char_count, token_count, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, noteId, chunkIndex, headingPath, content, charCount, tokenCount, timestamp(createdAt));
    }

    private void insertChatMessageSource(
            Long id,
            Long messageId,
            Long noteChunkId,
            int sourceOrder,
            double score,
            String createdAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO chat_message_sources
                            (id, message_id, note_chunk_id, source_order, score, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id, messageId, noteChunkId, sourceOrder, score, timestamp(createdAt));
    }

    private static Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
