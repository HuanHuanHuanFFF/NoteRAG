package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.huanf.noterag.entity.ChatMessageSource;
import com.huanf.noterag.model.ChatResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.LlmProperties;
import com.huanf.noterag.mapper.ChatMessageMapper;
import com.huanf.noterag.mapper.ChatMessageSourceMapper;
import com.huanf.noterag.mapper.ChatSessionMapper;
import com.huanf.noterag.entity.ChatMessage;
import com.huanf.noterag.entity.ChatMessageRole;
import com.huanf.noterag.entity.ChatMessageStatus;
import com.huanf.noterag.entity.ChatSession;
import com.huanf.noterag.entity.ChatSessionStatus;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.ChatPromptBuilder;
import com.huanf.noterag.rag.CitationMarkers;
import com.huanf.noterag.rag.RagPrompt;

class ChatServiceTests {

    private final ChatSessionMapper chatSessionMapper = mock(ChatSessionMapper.class);
    private final ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);
    private final ChatMessageSourceMapper chatMessageSourceMapper = mock(ChatMessageSourceMapper.class);
    private final QueryService queryService = mock(QueryService.class);
    private final ChatPromptBuilder chatPromptBuilder = mock(ChatPromptBuilder.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final LlmProperties llmProperties = llmProperties(true);
    private final TransactionTemplate transactionTemplate = transactionTemplate();
    private final ChatService chatService = new ChatService(
            chatSessionMapper,
            chatMessageMapper,
            chatMessageSourceMapper,
            queryService,
            chatPromptBuilder,
            llmClient,
            llmProperties,
            transactionTemplate);

    @Test
    void sendMessageCreatesSessionAndReturnsCitedSourcesOnly() {
        mockSessionInsert(10L);
        mockMessageInsert(101L, 102L);

        ChatMessage pendingAssistant = new ChatMessage(
                102L,
                10L,
                ChatMessageRole.ASSISTANT,
                "",
                ChatMessageStatus.PENDING,
                null,
                0,
                null,
                null);
        ChatMessage currentUser = new ChatMessage(
                101L,
                10L,
                ChatMessageRole.USER,
                "What does MVCC depend on?",
                ChatMessageStatus.COMPLETED,
                null,
                26,
                null,
                null);
        ChatMessage previousAssistant = new ChatMessage(
                88L,
                10L,
                ChatMessageRole.ASSISTANT,
                "旧回答" + CitationMarkers.format(900L),
                ChatMessageStatus.COMPLETED,
                null,
                10,
                null,
                null);
        when(chatMessageMapper.findPromptHistoryBySessionId(10L, 101L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of(previousAssistant));

        List<RetrievedChunk> rerankedSources = List.of(
                chunk(201L, 11L, "MySQL", "MVCC", "undo log", 0.91),
                chunk(202L, 12L, "MySQL", "MVCC", "read view", 0.82));
        when(queryService.querySources("What does MVCC depend on?")).thenReturn(rerankedSources);
        RagPrompt prompt = new RagPrompt("system", "user");
        when(chatPromptBuilder.build(any(), eq("What does MVCC depend on?"), eq(rerankedSources))).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("答案一" + CitationMarkers.format(12L) + "答案二" + CitationMarkers.format(11L));
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        ChatResult result = chatService.sendMessage(null, "  What does MVCC depend on?  ");

        assertThat(result.getSessionId()).isEqualTo(10L);
        assertThat(result.getSessionTitle()).isEqualTo(
                "What does MVCC depend on?".substring(0, ChatService.SESSION_TITLE_MAX_CHARS));
        assertThat(result.getUserMessageId()).isEqualTo(101L);
        assertThat(result.getAssistantMessageId()).isEqualTo(102L);
        assertThat(result.getAnswer()).isEqualTo("答案一" + CitationMarkers.format(12L) + "答案二" + CitationMarkers.format(11L));
        assertThat(result.getSources()).extracting(RetrievedChunk::getChunkId).containsExactly(12L, 11L);

        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatPromptBuilder).build(historyCaptor.capture(), eq("What does MVCC depend on?"), eq(rerankedSources));
        assertThat(historyCaptor.getValue()).hasSize(1);
        assertThat(historyCaptor.getValue().get(0).getId()).isEqualTo(88L);

        ArgumentCaptor<ChatMessage> completedAssistantCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).updateResult(completedAssistantCaptor.capture());
        assertThat(completedAssistantCaptor.getValue().getStatus()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThat(completedAssistantCaptor.getValue().getErrorCode()).isNull();
        assertThat(completedAssistantCaptor.getValue().getCharCount()).isEqualTo(result.getAnswer().length());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessageSource>> sourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageSourceMapper).batchInsert(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue()).hasSize(2);
        assertThat(sourceCaptor.getValue()).extracting("messageId").containsOnly(102L);
        assertThat(sourceCaptor.getValue()).extracting("noteChunkId").containsExactly(12L, 11L);
        assertThat(sourceCaptor.getValue()).extracting("sourceOrder").containsExactly(1, 2);
        assertThat(sourceCaptor.getValue()).extracting("score").containsExactly(0.82, 0.91);

        verify(chatSessionMapper, times(2)).updateLastMessageAt(eq(10L), any());
    }

    @Test
    void sendMessageContinuesExistingSession() {
        ChatSession existingSession = new ChatSession(5L, "Old title", ChatSessionStatus.ACTIVE, null, null, null);
        when(chatSessionMapper.findById(5L)).thenReturn(existingSession);
        mockMessageInsert(201L, 202L);
        when(chatMessageMapper.findPromptHistoryBySessionId(5L, 201L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of());
        List<RetrievedChunk> rerankedSources = List.of(chunk(301L, 31L, "Java", "JVM", "gc", 0.77));
        when(queryService.querySources("continue")).thenReturn(rerankedSources);
        RagPrompt prompt = new RagPrompt("system", "user");
        when(chatPromptBuilder.build(any(), eq("continue"), eq(rerankedSources))).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("answer" + CitationMarkers.format(31L));
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        ChatResult result = chatService.sendMessage(5L, " continue ");

        assertThat(result.getSessionId()).isEqualTo(5L);
        assertThat(result.getSessionTitle()).isEqualTo("Old title");
        verify(chatSessionMapper, never()).insert(any(ChatSession.class));
        verify(queryService).querySources("continue");
        verify(chatPromptBuilder).build(any(), eq("continue"), eq(rerankedSources));
        verify(llmClient).chat(prompt);
    }

    @Test
    void sendMessageMarksAssistantFailedWhenCitationInvalid() {
        ChatSession existingSession = new ChatSession(7L, "Old title", ChatSessionStatus.ACTIVE, null, null, null);
        when(chatSessionMapper.findById(7L)).thenReturn(existingSession);
        mockMessageInsert(301L, 302L);
        when(chatMessageMapper.findPromptHistoryBySessionId(7L, 301L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of());
        List<RetrievedChunk> rerankedSources = List.of(chunk(401L, 41L, "MySQL", "MVCC", "body", 0.88));
        when(queryService.querySources("question")).thenReturn(rerankedSources);
        RagPrompt prompt = new RagPrompt("system", "user");
        when(chatPromptBuilder.build(any(), eq("question"), eq(rerankedSources))).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("bad" + CitationMarkers.format(99L));
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        assertThatThrownBy(() -> chatService.sendMessage(7L, "question"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID);
                    assertThat(exception).hasMessage("LLM 返回了非法引用信息，请重试");
                });

        ArgumentCaptor<ChatMessage> failedAssistantCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).updateResult(failedAssistantCaptor.capture());
        ChatMessage failedAssistant = failedAssistantCaptor.getValue();
        assertThat(failedAssistant.getId()).isEqualTo(302L);
        assertThat(failedAssistant.getStatus()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(failedAssistant.getErrorCode()).isEqualTo(ChatService.ERROR_CODE_LLM_RESULT_INVALID);
        assertThat(failedAssistant.getContent()).isEmpty();
        verify(chatMessageSourceMapper, never()).batchInsert(any());
    }

    @Test
    void sendMessageAllowsAnswerWithoutCitationsAndReturnsEmptySources() {
        ChatSession existingSession = new ChatSession(8L, "Old title", ChatSessionStatus.ACTIVE, null, null, null);
        when(chatSessionMapper.findById(8L)).thenReturn(existingSession);
        mockMessageInsert(501L, 502L);
        when(chatMessageMapper.findPromptHistoryBySessionId(8L, 501L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of());
        List<RetrievedChunk> rerankedSources = List.of(chunk(601L, 61L, "MySQL", "MVCC", "body", 0.88));
        when(queryService.querySources("question")).thenReturn(rerankedSources);
        RagPrompt prompt = new RagPrompt("system", "user");
        when(chatPromptBuilder.build(any(), eq("question"), eq(rerankedSources))).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn("answer without citation");
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        ChatResult result = chatService.sendMessage(8L, "question");

        assertThat(result.getAnswer()).isEqualTo("answer without citation");
        assertThat(result.getSources()).isEmpty();
        ArgumentCaptor<ChatMessage> completedAssistantCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).updateResult(completedAssistantCaptor.capture());
        assertThat(completedAssistantCaptor.getValue().getId()).isEqualTo(502L);
        assertThat(completedAssistantCaptor.getValue().getStatus()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThat(completedAssistantCaptor.getValue().getErrorCode()).isNull();
        verify(chatMessageSourceMapper, never()).batchInsert(any());
    }

    @Test
    void sendMessageAllowsUnableToAnswerWithoutCitations() {
        ChatSession existingSession = new ChatSession(6L, "Old title", ChatSessionStatus.ACTIVE, null, null, null);
        when(chatSessionMapper.findById(6L)).thenReturn(existingSession);
        mockMessageInsert(701L, 702L);
        when(chatMessageMapper.findPromptHistoryBySessionId(6L, 701L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of());
        List<RetrievedChunk> rerankedSources = List.of(chunk(801L, 81L, "MySQL", "MVCC", "body", 0.88));
        when(queryService.querySources("question")).thenReturn(rerankedSources);
        RagPrompt prompt = new RagPrompt("system", "user");
        when(chatPromptBuilder.build(any(), eq("question"), eq(rerankedSources))).thenReturn(prompt);
        when(llmClient.chat(prompt)).thenReturn(ChatService.UNABLE_TO_ANSWER);
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        ChatResult result = chatService.sendMessage(6L, "question");

        assertThat(result.getAnswer()).isEqualTo(ChatService.UNABLE_TO_ANSWER);
        assertThat(result.getSources()).isEmpty();
        verify(chatMessageSourceMapper, never()).batchInsert(any());
    }

    @Test
    void sendMessageMarksAssistantFailedAsChatFailedWhenQuerySourcesFails() {
        ChatSession existingSession = new ChatSession(9L, "Old title", ChatSessionStatus.ACTIVE, null, null, null);
        when(chatSessionMapper.findById(9L)).thenReturn(existingSession);
        mockMessageInsert(401L, 402L);
        when(chatMessageMapper.findPromptHistoryBySessionId(9L, 401L, ChatService.HISTORY_LIMIT))
                .thenReturn(List.of());
        when(queryService.querySources("question"))
                .thenThrow(new BusinessException(CodeStatus.RERANK_FAILED, "rerank failed"));
        when(chatMessageMapper.updateResult(any(ChatMessage.class))).thenReturn(1);

        assertThatThrownBy(() -> chatService.sendMessage(9L, "question"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.RERANK_FAILED);
                    assertThat(exception).hasMessage("rerank failed");
                });

        ArgumentCaptor<ChatMessage> failedAssistantCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).updateResult(failedAssistantCaptor.capture());
        assertThat(failedAssistantCaptor.getValue().getId()).isEqualTo(402L);
        assertThat(failedAssistantCaptor.getValue().getStatus()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(failedAssistantCaptor.getValue().getErrorCode()).isEqualTo(ChatService.ERROR_CODE_CHAT_FAILED);
    }

    @Test
    void sendMessageRejectsMissingSession() {
        when(chatSessionMapper.findById(77L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(77L, "question"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.NOT_FOUND);
                    assertThat(exception).hasMessage("chat session not found");
                });
    }

    private void mockSessionInsert(Long sessionId) {
        when(chatSessionMapper.insert(any(ChatSession.class))).thenReturn(sessionId);
    }

    private void mockMessageInsert(Long userMessageId, Long assistantMessageId) {
        doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getRole() == ChatMessageRole.USER) {
                return userMessageId;
            } else {
                return assistantMessageId;
            }
        }).when(chatMessageMapper).insert(any(ChatMessage.class));
    }

    private static RetrievedChunk chunk(Long noteId, Long chunkId, String title, String headingPath, String content, Double score) {
        return new RetrievedChunk(noteId, chunkId, title, headingPath, content, score);
    }

    private static LlmProperties llmProperties(boolean enabled) {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) throws TransactionException {
            }

            @Override
            public void rollback(TransactionStatus status) throws TransactionException {
            }
        });
    }
}
