package com.huanf.noterag.dto;

import java.time.Instant;

import com.huanf.noterag.model.NoteListItem;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 笔记列表项响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteListItemResponse {

    private Long id;
    private String title;
    private Integer charCount;
    private Integer tokenCount;
    private Integer chunkCount;
    private Instant createdAt;

    public static NoteListItemResponse from(NoteListItem note) {
        return new NoteListItemResponse(
                note.getId(),
                note.getTitle(),
                note.getCharCount(),
                note.getTokenCount(),
                note.getChunkCount(),
                note.getCreatedAt());
    }
}
