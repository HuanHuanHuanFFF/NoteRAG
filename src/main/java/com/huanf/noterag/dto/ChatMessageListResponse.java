package com.huanf.noterag.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 聊天消息列表响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageListResponse {

    private List<ChatHistoryMessageResponse> messages;
}
