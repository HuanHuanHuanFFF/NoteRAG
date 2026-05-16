package com.huanf.noterag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {
    private Long noteId;
    private Long chunkId;
    private String title;
    private String headingPath;
    private String content;
    private Double score;
}
