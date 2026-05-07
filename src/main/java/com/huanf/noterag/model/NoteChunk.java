package com.huanf.noterag.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteChunk {

    private Long id;
    private Long noteId;
    private Integer chunkIndex;
    private String headingPath;
    private String content;
    private Integer charCount;
    private Integer tokenCount;
    private Instant createdAt;
}
