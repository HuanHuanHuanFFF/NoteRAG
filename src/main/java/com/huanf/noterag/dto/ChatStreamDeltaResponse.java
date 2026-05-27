package com.huanf.noterag.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat SSE delta 事件数据。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamDeltaResponse {
    private String text;
}
