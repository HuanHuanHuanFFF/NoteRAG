package com.huanf.noterag.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat 消息实体，对应 chat_messages 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private Long id;
    private Long sessionId;
    private ChatMessageRole role;
    private String content;
    private ChatMessageStatus status;

    /**
     * 消息失败时的业务错误码；成功或处理中时为空。
     */
    private String errorCode;

    private Integer charCount;
    private Instant createdAt;
    private Instant updatedAt;
}
