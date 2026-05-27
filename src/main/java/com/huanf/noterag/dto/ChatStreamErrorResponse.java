package com.huanf.noterag.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat SSE error 事件数据。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamErrorResponse {
    private int code;
    private String message;
}
