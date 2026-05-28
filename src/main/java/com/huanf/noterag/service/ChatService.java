package com.huanf.noterag.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.huanf.noterag.model.ChatMessageSourceChunk;
import com.huanf.noterag.model.ChatMessageWithSources;
import com.huanf.noterag.model.ChatResult;
import com.huanf.noterag.entity.ChatSession;
import com.huanf.noterag.entity.RecordStatus;
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
    static final int SESSION_RENAME_MAX_CHARS = 50;
    static final String ERROR_CODE_LLM_FAILED = "LLM_FAILED";
    static final String ERROR_CODE_LLM_RESULT_INVALID = "LLM_RESULT_INVALID";
    static final String ERROR_CODE_CHAT_FAILED = "CHAT_FAILED";
    static final String UNABLE_TO_ANSWER = "根据当前笔记内容无法确定";

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
        return sendMessage(sessionId, content, null);
    }

    /**
     * 发送一条限定笔记范围的 chat 消息并完成单轮非流式回答。
     */
    public ChatResult sendMessage(Long sessionId, String content, List<Long> noteIds) {
        return executeMessage(sessionId, content, noteIds, null, false);
    }

    /**
     * 发送一条限定笔记范围的 chat 消息并完成单轮流式回答。
     */
    public ChatResult streamMessage(Long sessionId, String content, List<Long> noteIds, StreamCallbacks callbacks) {
        if (callbacks == null) {
            throw new IllegalArgumentException("callbacks must not be null");
        }
        return executeMessage(sessionId, content, noteIds, callbacks, true);
    }

    /**
     * 复用同步与 SSE 的 chat 主链路，只在 LLM 调用和事件回调处区分执行方式。
     */
    private ChatResult executeMessage(
            Long sessionId,
            String content,
            List<Long> noteIds,
            StreamCallbacks callbacks,
            boolean streaming
    ) {
        validateStreamCallbacks(streaming, callbacks);
        ensureLlmEnabled();
        String normalizedContent = normalizeContent(content);
        log.info("Chat 发送开始, sessionId={}, contentLength={}, noteScopeCount={}",
                sessionId, normalizedContent.length(), noteScopeCount(noteIds));
        long startNanos = System.nanoTime();

        PendingChatContext pendingContext = transactionTemplate.execute(status ->
                initializePendingContext(sessionId, normalizedContent));
        if (pendingContext == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Chat init transaction returned no result");
        }
        log.info("Chat pending 初始化完成, sessionId={}, userMessageId={}, assistantMessageId={}",
                pendingContext.session().getId(),
                pendingContext.userMessage().getId(),
                pendingContext.assistantMessage().getId());
        if (callbacks != null) {
            callbacks.onMeta(toStreamMeta(pendingContext));
        }

        try {
            List<ChatMessage> historyMessages = chatMessageMapper.findPromptHistoryBySessionId(
                    pendingContext.session().getId(),
                    pendingContext.userMessage().getId(),
                    HISTORY_LIMIT);
            List<RetrievedChunk> rerankedSources = querySources(normalizedContent, noteIds);
            log.info("Chat 候选 source chunkIds, sessionId={}, userMessageId={}, sourceCount={}, chunkIds={}",
                    pendingContext.session().getId(),
                    pendingContext.userMessage().getId(),
                    rerankedSources.size(),
                    formatChunkIdsForLog(rerankedSources));
            RagPrompt prompt = chatPromptBuilder.build(historyMessages, normalizedContent, rerankedSources);
            log.info("Chat prompt 构建完成, sessionId={}, userMessageId={}, historyCount={}, sourceCount={}, systemPromptLength={}, userPromptLength={}",
                    pendingContext.session().getId(),
                    pendingContext.userMessage().getId(),
                    historyMessages.size(),
                    rerankedSources.size(),
                    prompt.system().length(),
                    prompt.user().length());
            String answer = streaming
                    ? llmClient.streamChat(prompt, callbacks::onDelta)
                    : llmClient.chat(prompt);
            log.debug("LLM answer={}", answer);
            List<RetrievedChunk> citedSources = filterSourcesByAnswerCitations(answer, rerankedSources);
            log.info("Chat citation 过滤完成, sessionId={}, userMessageId={}, assistantMessageId={}, candidateSourceCount={}, citedSourceCount={}",
                    pendingContext.session().getId(),
                    pendingContext.userMessage().getId(),
                    pendingContext.assistantMessage().getId(),
                    rerankedSources.size(),
                    citedSources.size());

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
     * 校验同步/流式模式与回调参数一致，避免后续私有方法扩展时误传。
     */
    private void validateStreamCallbacks(boolean streaming, StreamCallbacks callbacks) {
        if (streaming && callbacks == null) {
            throw new IllegalArgumentException("callbacks must not be null when streaming is enabled");
        }
        if (!streaming && callbacks != null) {
            throw new IllegalArgumentException("callbacks must be null when streaming is disabled");
        }
    }

    /**
     * 将已落库的 pending 上下文转换成 SSE meta 事件数据。
     */
    private StreamMeta toStreamMeta(PendingChatContext pendingContext) {
        return new StreamMeta(
                pendingContext.session().getId(),
                pendingContext.session().getTitle(),
                pendingContext.userMessage().getId(),
                pendingContext.assistantMessage().getId());
    }

    /**
     * 查询所有 chat 会话，用于前端恢复会话列表。
     */
    public List<ChatSession> listSessions() {
        return chatSessionMapper.findAll();
    }

    /**
     * 查询指定会话的消息历史，并为 assistant 消息组装已落库的引用来源。
     */
    public List<ChatMessageWithSources> listMessages(Long sessionId) {
        requireExistingSession(sessionId);
        List<ChatMessage> messages = chatMessageMapper.findBySessionId(sessionId);
        List<Long> assistantMessageIds = messages.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .map(ChatMessage::getId)
                .toList();

        Map<Long, List<RetrievedChunk>> sourcesByMessageId = loadSourcesByMessageId(assistantMessageIds);
        return messages.stream()
                .map(message -> new ChatMessageWithSources(
                        message,
                        sourcesByMessageId.getOrDefault(message.getId(), List.of())))
                .toList();
    }

    /**
     * 归档 ACTIVE 会话；归档后不能继续发消息，也不会出现在会话列表。
     */
    public void archiveSession(Long sessionId) {
        int archived = chatSessionMapper.archiveById(sessionId);
        if (archived != 1) {
            throw new BusinessException(CodeStatus.NOT_FOUND, "chat session not found");
        }
        log.info("Chat 会话已归档, sessionId={}", sessionId);
    }

    /**
     * 重命名 ACTIVE 会话，只更新标题和 updated_at，不改变 lastMessageAt。
     */
    public ChatSession renameSession(Long sessionId, String title) {
        String normalizedTitle = normalizeSessionTitle(title);
        int updated = chatSessionMapper.updateTitle(sessionId, normalizedTitle);
        if (updated != 1) {
            throw new BusinessException(CodeStatus.NOT_FOUND, "chat session not found");
        }
        log.info("Chat 会话已重命名, sessionId={}, titleLength={}", sessionId, normalizedTitle.length());
        return requireExistingSession(sessionId);
    }

    /**
     * 按 assistant messageId 批量加载已持久化的引用来源，并组装成消息到 sources 的映射。
     */
    private Map<Long, List<RetrievedChunk>> loadSourcesByMessageId(List<Long> messageIds) {
        Map<Long, List<RetrievedChunk>> sourcesByMessageId = new LinkedHashMap<>();
        if (messageIds.isEmpty()) {
            return sourcesByMessageId;
        }
        List<ChatMessageSourceChunk> chunksByMessageIds = chatMessageSourceMapper.findSourceChunksByMessageIds(messageIds);
        for (ChatMessageSourceChunk source : chunksByMessageIds) {
            sourcesByMessageId
                    .computeIfAbsent(source.getMessageId(), ignored -> new ArrayList<>())
                    .add(source.toRetrievedChunk());
        }
        return sourcesByMessageId;
    }

    /**
     * 按可选 note 范围查询本轮候选 sources。
     */
    private List<RetrievedChunk> querySources(String question, List<Long> noteIds) {
        if (noteIds == null) {
            return queryService.querySources(question);
        }
        return queryService.querySources(question, noteIds);
    }

    /**
     * 统计本轮请求传入的 note scope 数量，实际清理由 RetrievalService 完成。
     */
    private int noteScopeCount(List<Long> noteIds) {
        return noteIds == null ? 0 : noteIds.size();
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
        session.setStatus(RecordStatus.ACTIVE);
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
     * 将候选 chunkId 压成单行日志文本，避免 INFO 日志输出完整 chunk 内容。
     */
    private String formatChunkIdsForLog(List<RetrievedChunk> sources) {
        return sources.stream()
                .map(RetrievedChunk::getChunkId)
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
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

    /**
     * 规范化会话标题，并限制手动重命名标题长度。
     */
    private String normalizeSessionTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("title must not be null");
        }
        String normalized = title.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (normalized.length() > SESSION_RENAME_MAX_CHARS) {
            throw new IllegalArgumentException("title must not be greater than " + SESSION_RENAME_MAX_CHARS + " characters");
        }
        return normalized;
    }

    private record PendingChatContext(ChatSession session, ChatMessage userMessage, ChatMessage assistantMessage) {
    }

    public record StreamMeta(Long sessionId, String sessionTitle, Long userMessageId, Long assistantMessageId) {
    }

    public interface StreamCallbacks {

        void onMeta(StreamMeta meta);

        void onDelta(String delta);
    }
}
