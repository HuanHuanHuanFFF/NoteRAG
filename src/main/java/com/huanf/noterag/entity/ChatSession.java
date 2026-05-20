package com.huanf.noterag.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat 会话实体，对应 chat_sessions 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    private Long id;
    private String title;
    private ChatSessionStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * 会话最后一条消息的时间，用于会话列表按活跃度排序。
     */
    private Instant lastMessageAt;
}
