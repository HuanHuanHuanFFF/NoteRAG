package com.huanf.noterag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageSourceChunk {

    private Long messageId;
    private Long noteId;
    private Long chunkId;
    private String title;
    private String headingPath;
    private String content;
    private Double score;

    public RetrievedChunk toRetrievedChunk() {
        return new RetrievedChunk(noteId, chunkId, title, headingPath, content, score);
    }
}
