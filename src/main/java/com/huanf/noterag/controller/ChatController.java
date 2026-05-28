package com.huanf.noterag.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.ChatSseProperties;
import com.huanf.noterag.dto.ChatMessageResponse;
import com.huanf.noterag.dto.ChatMessageListResponse;
import com.huanf.noterag.dto.ChatHistoryMessageResponse;
import com.huanf.noterag.dto.ChatSessionListResponse;
import com.huanf.noterag.dto.ChatSessionResponse;
import com.huanf.noterag.dto.ChatStreamDeltaResponse;
import com.huanf.noterag.dto.ChatStreamErrorResponse;
import com.huanf.noterag.dto.ChatStreamMetaResponse;
import com.huanf.noterag.dto.RenameChatSessionRequest;
import com.huanf.noterag.dto.SendChatMessageRequest;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.ChatResult;
import com.huanf.noterag.service.ChatService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ChatSseProperties chatSseProperties;
    private final TaskExecutor chatSseTaskExecutor;

    public ChatController(
            ChatService chatService,
            ChatSseProperties chatSseProperties,
            @Qualifier("chatSseTaskExecutor") TaskExecutor chatSseTaskExecutor
    ) {
        this.chatService = chatService;
        this.chatSseProperties = chatSseProperties;
        this.chatSseTaskExecutor = chatSseTaskExecutor;
    }

    @PostMapping(value = "/chat-sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatMessageResponse sendFirstMessage(@Valid @RequestBody SendChatMessageRequest request) {
        log.info("Chat 首条消息请求, contentLength={}, noteScopeCount={}",
                request.getContent().length(), noteScopeCount(request.getNoteIds()));
        return toResponse(chatService.sendMessage(null, request.getContent(), request.getNoteIds()));
    }

    @PostMapping(value = "/chat-sessions/{sessionId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatMessageResponse sendMessage(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        log.info("Chat 追加消息请求, sessionId={}, contentLength={}, noteScopeCount={}",
                sessionId, request.getContent().length(), noteScopeCount(request.getNoteIds()));
        return toResponse(chatService.sendMessage(sessionId, request.getContent(), request.getNoteIds()));
    }

    @PostMapping(value = "/chat-sessions/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFirstMessage(@Valid @RequestBody SendChatMessageRequest request) {
        log.info("Chat SSE 首条消息请求, contentLength={}, noteScopeCount={}",
                request.getContent().length(), noteScopeCount(request.getNoteIds()));
        return createChatStream(null, request);
    }

    @PostMapping(value = "/chat-sessions/{sessionId}/messages/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        log.info("Chat SSE 追加消息请求, sessionId={}, contentLength={}, noteScopeCount={}",
                sessionId, request.getContent().length(), noteScopeCount(request.getNoteIds()));
        return createChatStream(sessionId, request);
    }

    @GetMapping("/chat-sessions")
    public ChatSessionListResponse listSessions() {
        return new ChatSessionListResponse(
                chatService.listSessions().stream()
                        .map(ChatSessionResponse::from)
                        .toList());
    }

    @GetMapping("/chat-sessions/{sessionId}/messages")
    public ChatMessageListResponse listMessages(@PathVariable("sessionId") Long sessionId) {
        return new ChatMessageListResponse(
                chatService.listMessages(sessionId).stream()
                        .map(ChatHistoryMessageResponse::from)
                        .toList());
    }

    @DeleteMapping("/chat-sessions/{sessionId}")
    public void archiveSession(@PathVariable("sessionId") Long sessionId) {
        log.info("Chat 会话归档请求, sessionId={}", sessionId);
        chatService.archiveSession(sessionId);
    }

    @PatchMapping(value = "/chat-sessions/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatSessionResponse renameSession(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody RenameChatSessionRequest request
    ) {
        log.info("Chat 会话重命名请求, sessionId={}, titleLength={}", sessionId, request.getTitle().strip().length());
        return ChatSessionResponse.from(chatService.renameSession(sessionId, request.getTitle()));
    }

    /**
     * 将 chat service 返回结果转换成 HTTP 响应 DTO。
     */
    private ChatMessageResponse toResponse(ChatResult result) {
        return new ChatMessageResponse(
                result.getSessionId(),
                result.getSessionTitle(),
                result.getUserMessageId(),
                result.getAssistantMessageId(),
                result.getAnswer(),
                result.getSources().stream()
                        .map(SourceChunkResponse::from)
                        .toList());
    }

    /**
     * 创建 SSE 连接并把 chat 主链路投递到 Spring 管理的后台线程池。
     */
    private SseEmitter createChatStream(Long sessionId, SendChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(chatSseProperties.getSseTimeoutMs());
        AtomicBoolean clientConnected = new AtomicBoolean(true);
        registerEmitterCallbacks(sessionId, emitter, clientConnected);

        try {
            chatSseTaskExecutor.execute(() -> runChatStream(sessionId, request, emitter, clientConnected));
        } catch (RuntimeException exception) {
            log.error("Chat SSE 任务提交失败, sessionId={}", sessionId, exception);
            sendSseError(
                    emitter,
                    clientConnected,
                    sessionId,
                    null,
                    CodeStatus.INTERNAL_ERROR.getCode(),
                    CodeStatus.INTERNAL_ERROR.getMessage());
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 注册 SSE 生命周期回调；连接断开或超时只影响事件发送，不取消后端生成任务。
     */
    private void registerEmitterCallbacks(Long sessionId, SseEmitter emitter, AtomicBoolean clientConnected) {
        emitter.onCompletion(() -> {
            clientConnected.set(false);
            log.info("Chat SSE 连接完成, sessionId={}", sessionId);
        });
        emitter.onTimeout(() -> {
            clientConnected.set(false);
            log.warn("Chat SSE 连接超时, sessionId={}", sessionId);
            emitter.complete();
        });
        emitter.onError(error -> {
            clientConnected.set(false);
            log.warn("Chat SSE 连接异常, sessionId={}, error={}", sessionId, error.toString());
        });
    }

    /**
     * 执行 SSE chat 主链路，并把 service 回调转换成 SSE 事件。
     */
    private void runChatStream(
            Long sessionId,
            SendChatMessageRequest request,
            SseEmitter emitter,
            AtomicBoolean clientConnected
    ) {
        AtomicReference<ChatService.StreamMeta> streamMetaRef = new AtomicReference<>();
        try {
            ChatResult result = chatService.streamMessage(
                    sessionId,
                    request.getContent(),
                    request.getNoteIds(),
                    new ChatService.StreamCallbacks() {
                        @Override
                        public void onMeta(ChatService.StreamMeta meta) {
                            streamMetaRef.set(meta);
                            trySend(emitter, clientConnected, "meta", new ChatStreamMetaResponse(
                                    meta.sessionId(),
                                    meta.sessionTitle(),
                                    meta.userMessageId(),
                                    meta.assistantMessageId()));
                        }

                        @Override
                        public void onDelta(String delta) {
                            trySend(emitter, clientConnected, "delta", new ChatStreamDeltaResponse(delta));
                        }
                    });
            log.info("Chat SSE done 发送, sessionId={}, userMessageId={}, assistantMessageId={}",
                    result.getSessionId(), result.getUserMessageId(), result.getAssistantMessageId());
            trySend(emitter, clientConnected, "done", toResponse(result));
        } catch (BusinessException exception) {
            sendSseError(
                    emitter,
                    clientConnected,
                    sessionId,
                    streamMetaRef.get(),
                    exception.getCodeStatus().getCode(),
                    exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendSseError(
                    emitter,
                    clientConnected,
                    sessionId,
                    streamMetaRef.get(),
                    CodeStatus.INVALID_REQUEST.getCode(),
                    exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Chat SSE 处理异常, sessionId={}", sessionId, exception);
            sendSseError(
                    emitter,
                    clientConnected,
                    sessionId,
                    streamMetaRef.get(),
                    CodeStatus.INTERNAL_ERROR.getCode(),
                    CodeStatus.INTERNAL_ERROR.getMessage());
        } finally {
            emitter.complete();
        }
    }

    /**
     * 发送 SSE error 前记录结构化上下文，便于前后端联调定位失败发生在哪个消息。
     */
    private void sendSseError(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            Long requestSessionId,
            ChatService.StreamMeta streamMeta,
            int code,
            String message
    ) {
        log.info("Chat SSE error 发送, requestSessionId={}, sessionId={}, userMessageId={}, assistantMessageId={}, code={}",
                requestSessionId,
                streamMeta == null ? null : streamMeta.sessionId(),
                streamMeta == null ? null : streamMeta.userMessageId(),
                streamMeta == null ? null : streamMeta.assistantMessageId(),
                code);
        trySend(emitter, clientConnected, "error", new ChatStreamErrorResponse(code, message));
    }

    /**
     * 安全发送 SSE 事件；发送失败只关闭发送侧，不能影响后端 chat 主链路继续落库。
     */
    boolean trySend(SseEmitter emitter, AtomicBoolean clientConnected, String eventName, Object data) {
        if (!clientConnected.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException exception) {
            if (clientConnected.compareAndSet(true, false)) {
                log.info("Chat SSE 发送失败，停止继续发送事件, eventName={}, error={}",
                        eventName, exception.toString());
            }
            return false;
        }
    }

    /**
     * 统计请求中的 note scope 数量，仅用于日志，不在 controller 做 scope 清理。
     */
    private int noteScopeCount(List<Long> noteIds) {
        return noteIds == null ? 0 : noteIds.size();
    }
}
