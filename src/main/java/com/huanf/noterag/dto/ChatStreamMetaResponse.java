package com.huanf.noterag.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat SSE meta 事件数据。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamMetaResponse {
    private Long sessionId;
    private String sessionTitle;
    private Long userMessageId;
    private Long assistantMessageId;
}
