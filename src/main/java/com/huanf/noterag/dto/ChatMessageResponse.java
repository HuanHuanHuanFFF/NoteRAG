package com.huanf.noterag.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long sessionId;
    private String sessionTitle;
    private Long userMessageId;
    private Long assistantMessageId;
    private String answer;
    private List<SourceChunkResponse> sources;
}
