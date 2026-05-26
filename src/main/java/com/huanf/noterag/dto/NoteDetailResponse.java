package com.huanf.noterag.dto;

import java.time.Instant;

import com.huanf.noterag.entity.Note;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 笔记详情响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteDetailResponse {

    private Long id;
    private String title;
    private String content;
    private Integer charCount;
    private Integer tokenCount;
    private Instant createdAt;

    public static NoteDetailResponse from(Note note) {
        return new NoteDetailResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getCharCount(),
                note.getTokenCount(),
                note.getCreatedAt());
    }
}
