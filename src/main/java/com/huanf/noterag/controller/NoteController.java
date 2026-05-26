package com.huanf.noterag.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.dto.NoteDetailResponse;
import com.huanf.noterag.dto.NoteListResponse;
import com.huanf.noterag.dto.NoteListItemResponse;
import com.huanf.noterag.service.NoteService;

import jakarta.validation.Valid;

/**
 * Note 导入与查询接口。
 */
@RestController
@RequestMapping("/api")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping(value = "/note-imports/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportTextResponse importText(@Valid @RequestBody ImportTextRequest request) {
        return noteService.importText(request);
    }

    @GetMapping("/notes")
    public NoteListResponse listNotes() {
        return new NoteListResponse(
                noteService.listNotes().stream()
                        .map(NoteListItemResponse::from)
                        .toList());
    }

    @GetMapping("/notes/{noteId}")
    public NoteDetailResponse getNoteDetail(@PathVariable("noteId") Long noteId) {
        return NoteDetailResponse.from(noteService.getNoteDetail(noteId));
    }

    @DeleteMapping("/notes/{noteId}")
    public void archiveNote(@PathVariable("noteId") Long noteId) {
        noteService.archiveNote(noteId);
    }
}
