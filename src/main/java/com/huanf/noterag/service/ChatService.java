package com.huanf.noterag.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
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
import com.huanf.noterag.entity.ChatMessageSource;
import com.huanf.noterag.entity.ChatMessageStatus;
import com.huanf.noterag.model.ChatResult;
import com.huanf.noterag.entity.ChatSession;
import com.huanf.noterag.entity.ChatSessionStatus;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.AnswerCitationExtractor;
import com.huanf.noterag.rag.ChatPromptBuilder;
import com.huanf.noterag.rag.RagPrompt;

import lombok.extern.slf4j.Slf4j;

/**
 * 单会话 chat 主链路编排。
 *
 * <p>该服务只负责“会话落库 -> 检索 sources -> prompt -> LLM -> 回写 assistant 结果”的非流式闭环。
 * 外部 retrieval、rerank、LLM 调用保持在短事务之外，避免长事务占用数据库连接。</p>
 */
@Slf4j
@Service
public class ChatService {

    static final int HISTORY_LIMIT = 25;
    static final int SESSION_TITLE_MAX_CHARS = 17;
    static final String ERROR_CODE_LLM_FAILED = "LLM_FAILED";
    static final String ERROR_CODE_LLM_RESULT_INVALID = "LLM_RESULT_INVALID";
    static final String ERROR_CODE_CHAT_FAILED = "CHAT_FAILED";
    static final String ERROR_CODE_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageSourceMapper chatMessageSourceMapper;
    private final QueryService queryService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final TransactionTemplate transactionTemplate;

    public ChatService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatMessageSourceMapper chatMessageSourceMapper,
            QueryService queryService,
            ChatPromptBuilder chatPromptBuilder,
            LlmClient llmClient,
            LlmProperties llmProperties,
            TransactionTemplate transactionTemplate
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMessageSourceMapper = chatMessageSourceMapper;
        this.queryService = queryService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 发送一条 chat 消息并完成单轮非流式回答。
     */
    public ChatResult sendMessage(Long sessionId, String content) {
        ensureLlmEnabled();
        String normalizedContent = normalizeContent(content);
        log.info("Chat 发送开始, sessionId={}, contentLength={}", sessionId, normalizedContent.length());
        long startNanos = System.nanoTime();

        PendingChatContext pendingContext = transactionTemplate.execute(status ->
                initializePendingContext(sessionId, normalizedContent));
        if (pendingContext == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Chat init transaction returned no result");
        }

        try {
            List<ChatMessage> historyMessages = chatMessageMapper.findPromptHistoryBySessionId(
                    pendingContext.session().getId(),
                    pendingContext.userMessage().getId(),
                    HISTORY_LIMIT);
            List<RetrievedChunk> rerankedSources = queryService.querySources(normalizedContent);
            RagPrompt prompt = chatPromptBuilder.build(historyMessages, normalizedContent, rerankedSources);
            String answer = llmClient.chat(prompt);
            List<RetrievedChunk> citedSources = filterSourcesByAnswerCitations(answer, rerankedSources);

            ChatResult result = transactionTemplate.execute(status ->
                    completeAssistantMessage(pendingContext, answer, citedSources));
            if (result == null) {
                throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Chat completion transaction returned no result");
            }

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("Chat 发送完成, sessionId={}, userMessageId={}, assistantMessageId={}, sourceCount={}, answerLength={}, elapsedMs={}",
                    result.getSessionId(),
                    result.getUserMessageId(),
                    result.getAssistantMessageId(),
                    result.getSources().size(),
                    result.getAnswer().length(),
                    elapsedMs);
            return result;
        } catch (BusinessException exception) {
            markAssistantFailed(pendingContext.assistantMessage().getId(), mapFailureCode(exception));
            throw exception;
        } catch (RuntimeException exception) {
            markAssistantFailed(pendingContext.assistantMessage().getId(), ERROR_CODE_LLM_FAILED);
            throw exception;
        }
    }

    /**
     * 校验 chat 链路当前是否允许调用 LLM。
     */
    private void ensureLlmEnabled() {
        if (!llmProperties.isEnabled()) {
            throw new BusinessException(CodeStatus.LLM_CONFIG_INVALID, "LLM is disabled for chat");
        }
    }

    /**
     * 在短事务内初始化本轮会话上下文，先落 user 消息，再创建 assistant pending 消息。
     */
    private PendingChatContext initializePendingContext(Long sessionId, String normalizedContent) {
        ChatSession session = sessionId == null
                ? createSession(normalizedContent)
                : requireExistingSession(sessionId);

        Instant userMessageAt = Instant.now();
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(normalizedContent);
        userMessage.setStatus(ChatMessageStatus.COMPLETED);
        userMessage.setErrorCode(null);
        userMessage.setCharCount(normalizedContent.length());
        Long userMessageId = chatMessageMapper.insert(userMessage);
        if (userMessageId == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "user message insert returned no id");
        }
        userMessage.setId(userMessageId);
        chatSessionMapper.updateLastMessageAt(session.getId(), userMessageAt);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setStatus(ChatMessageStatus.PENDING);
        assistantMessage.setErrorCode(null);
        assistantMessage.setCharCount(0);
        Long assistantMessageId = chatMessageMapper.insert(assistantMessage);
        if (assistantMessageId == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "assistant message insert returned no id");
        }
        assistantMessage.setId(assistantMessageId);

        return new PendingChatContext(session, userMessage, assistantMessage);
    }

    /**
     * 创建新会话，并用首条用户消息生成默认标题。
     */
    private ChatSession createSession(String normalizedContent) {
        ChatSession session = new ChatSession();
        session.setTitle(buildSessionTitle(normalizedContent));
        session.setStatus(ChatSessionStatus.ACTIVE);
        Long sessionId = chatSessionMapper.insert(session);
        if (sessionId == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "chat session insert returned no id");
        }
        session.setId(sessionId);
        return session;
    }

    /**
     * 将首条消息压成单行后截断成默认会话标题。
     */
    private String buildSessionTitle(String content) {
        String singleLine = content.replaceAll("\\s+", " ").strip();
        if (singleLine.length() <= SESSION_TITLE_MAX_CHARS) {
            return singleLine;
        }
        return singleLine.substring(0, SESSION_TITLE_MAX_CHARS);
    }

    /**
     * 加载已有会话，不存在时按业务错误返回。
     */
    private ChatSession requireExistingSession(Long sessionId) {
        ChatSession session = chatSessionMapper.findById(sessionId);
        if (session == null) {
            throw new BusinessException(CodeStatus.NOT_FOUND, "chat session not found");
        }
        return session;
    }

    /**
     * 根据 answer 中的 citation marker 按引用顺序过滤出真正被引用的 sources。
     */
    private List<RetrievedChunk> filterSourcesByAnswerCitations(String answer, List<RetrievedChunk> rerankedSources) {
        List<Long> citedSourceIds;
        try {
            citedSourceIds = AnswerCitationExtractor.extractSourceIds(answer);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM 返回了非法引用信息，请重试", exception);
        }

        Map<Long, RetrievedChunk> sourceByChunkId = new LinkedHashMap<>();
        for (RetrievedChunk source : rerankedSources) {
            sourceByChunkId.putIfAbsent(source.getChunkId(), source);
        }

        List<RetrievedChunk> citedSources = new ArrayList<>(citedSourceIds.size());
        for (Long citedSourceId : citedSourceIds) {
            RetrievedChunk chunk = sourceByChunkId.get(citedSourceId);
            if (chunk == null) {
                throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "LLM 返回了非法引用信息，请重试");
            }
            citedSources.add(chunk);
        }
        return citedSources;
    }

    /**
     * 在短事务内回写 assistant 最终结果，并持久化被引用的 sources。
     */
    private ChatResult completeAssistantMessage(
            PendingChatContext pendingContext,
            String answer,
            List<RetrievedChunk> citedSources
    ) {
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setId(pendingContext.assistantMessage().getId());
        assistantMessage.setContent(answer);
        assistantMessage.setStatus(ChatMessageStatus.COMPLETED);
        assistantMessage.setErrorCode(null);
        assistantMessage.setCharCount(answer.length());
        int updated = chatMessageMapper.updateResult(assistantMessage);
        if (updated != 1) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "assistant message update failed");
        }

        if (!citedSources.isEmpty()) {
            chatMessageSourceMapper.batchInsert(toMessageSources(assistantMessage.getId(), citedSources));
        }

        chatSessionMapper.updateLastMessageAt(pendingContext.session().getId(), Instant.now());
        return new ChatResult(
                pendingContext.session().getId(),
                pendingContext.session().getTitle(),
                pendingContext.userMessage().getId(),
                pendingContext.assistantMessage().getId(),
                answer,
                citedSources);
    }

    /**
     * 将最终引用的 chunk 列表转换成 chat_message_sources 持久化对象。
     */
    private List<ChatMessageSource> toMessageSources(Long assistantMessageId, List<RetrievedChunk> citedSources) {
        List<ChatMessageSource> messageSources = new ArrayList<>(citedSources.size());
        for (int i = 0; i < citedSources.size(); i++) {
            RetrievedChunk source = citedSources.get(i);
            ChatMessageSource messageSource = new ChatMessageSource();
            messageSource.setMessageId(assistantMessageId);
            messageSource.setNoteChunkId(source.getChunkId());
            messageSource.setSourceOrder(i + 1);
            messageSource.setScore(source.getScore());
            messageSources.add(messageSource);
        }
        return messageSources;
    }

    /**
     * 在回答失败时，把 assistant pending 消息回写为 FAILED。
     */
    private void markAssistantFailed(Long assistantMessageId, String errorCode) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ChatMessage assistantMessage = new ChatMessage();
                assistantMessage.setId(assistantMessageId);
                assistantMessage.setContent("");
                assistantMessage.setStatus(ChatMessageStatus.FAILED);
                assistantMessage.setErrorCode(errorCode);
                assistantMessage.setCharCount(0);
                int updated = chatMessageMapper.updateResult(assistantMessage);
                if (updated != 1) {
                    log.error("Assistant FAILED 回写失败, assistantMessageId={}, errorCode={}", assistantMessageId, errorCode);
                }
            });
        } catch (RuntimeException exception) {
            log.error("Assistant FAILED 回写异常, assistantMessageId={}, errorCode={}", assistantMessageId, errorCode, exception);
        }
    }

    /**
     * 将业务异常映射为稳定的 assistant 失败码。
     */
    private String mapFailureCode(BusinessException exception) {
        if (exception.getCodeStatus() == CodeStatus.LLM_RESULT_INVALID) {
            return ERROR_CODE_LLM_RESULT_INVALID;
        }
        if (exception.getCodeStatus() == CodeStatus.LLM_FAILED) {
            return ERROR_CODE_LLM_FAILED;
        }
        return ERROR_CODE_CHAT_FAILED;
    }

    /**
     * 规范化用户输入内容，统一换行并拒绝空白消息。
     */
    private String normalizeContent(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        return normalized;
    }

    private record PendingChatContext(ChatSession session, ChatMessage userMessage, ChatMessage assistantMessage) {
    }
}
