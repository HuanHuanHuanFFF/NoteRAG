package com.huanf.noterag.dto;

import com.huanf.noterag.model.RetrievedChunk;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SourceChunkResponse {

    private Long noteId;
    private Long chunkId;
    private String title;
    private String headingPath;
    private String content;
    private Double score;

    public static SourceChunkResponse from(RetrievedChunk chunk) {
        return new SourceChunkResponse(
                chunk.getNoteId(),
                chunk.getChunkId(),
                chunk.getTitle(),
                chunk.getHeadingPath(),
                chunk.getContent(),
                chunk.getScore());
    }
}
