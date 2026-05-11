package com.huanf.noterag.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huanf.noterag.dto.ImportTextRequest;
import com.huanf.noterag.dto.ImportTextResponse;
import com.huanf.noterag.service.NoteImportService;

import jakarta.validation.Valid;

/**
 * Note 导入相关接口。
 */
@RestController
@RequestMapping("/api")
public class NoteImportController {

    private final NoteImportService noteImportService;

    public NoteImportController(NoteImportService noteImportService) {
        this.noteImportService = noteImportService;
    }

    @PostMapping(value = "/note-imports/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportTextResponse importText(@Valid @RequestBody ImportTextRequest request) {
        return noteImportService.importText(request);
    }
}
