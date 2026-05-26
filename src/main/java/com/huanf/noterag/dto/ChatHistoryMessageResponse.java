package com.huanf.noterag.dto;

import java.time.Instant;
import java.util.List;

import com.huanf.noterag.entity.ChatMessage;
import com.huanf.noterag.entity.ChatMessageRole;
import com.huanf.noterag.entity.ChatMessageStatus;
import com.huanf.noterag.model.ChatMessageWithSources;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天历史消息响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryMessageResponse {

    private Long id;
    private ChatMessageRole role;
    private String content;
    private ChatMessageStatus status;
    private String errorCode;
    private Instant createdAt;
    private List<SourceChunkResponse> sources;

    public static ChatHistoryMessageResponse from(ChatMessageWithSources messageWithSources) {
        ChatMessage message = messageWithSources.getMessage();
        return new ChatHistoryMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getStatus(),
                message.getErrorCode(),
                message.getCreatedAt(),
                messageWithSources.getSources().stream()
                        .map(SourceChunkResponse::from)
                        .toList());
    }
}
