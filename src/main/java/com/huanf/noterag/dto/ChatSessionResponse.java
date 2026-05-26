package com.huanf.noterag.dto;

import java.time.Instant;

import com.huanf.noterag.entity.ChatSession;
import com.huanf.noterag.entity.RecordStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天会话项响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {

    private Long id;
    private String title;
    private RecordStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastMessageAt;

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getLastMessageAt());
    }
}
