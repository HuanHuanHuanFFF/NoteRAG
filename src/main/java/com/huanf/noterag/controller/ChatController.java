package com.huanf.noterag.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huanf.noterag.dto.ChatMessageResponse;
import com.huanf.noterag.dto.ChatMessageListResponse;
import com.huanf.noterag.dto.ChatHistoryMessageResponse;
import com.huanf.noterag.dto.ChatSessionListResponse;
import com.huanf.noterag.dto.ChatSessionResponse;
import com.huanf.noterag.dto.SendChatMessageRequest;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.ChatResult;
import com.huanf.noterag.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/chat-sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatMessageResponse sendFirstMessage(@Valid @RequestBody SendChatMessageRequest request) {
        return toResponse(chatService.sendMessage(null, request.getContent()));
    }

    @PostMapping(value = "/chat-sessions/{sessionId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatMessageResponse sendMessage(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        return toResponse(chatService.sendMessage(sessionId, request.getContent()));
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
}
