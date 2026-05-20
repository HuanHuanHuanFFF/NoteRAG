package com.huanf.noterag.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatResult {
    private Long sessionId;
    private String sessionTitle;
    private Long userMessageId;
    private Long assistantMessageId;
    private String answer;
    private List<RetrievedChunk> sources;
}
