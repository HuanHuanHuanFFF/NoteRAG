package com.huanf.noterag.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 笔记列表摘要模型。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteListItem {
    private Long id;
    private String title;
    private Integer charCount;
    private Integer tokenCount;
    private Integer chunkCount;
    private Instant createdAt;
}
