package com.huanf.noterag.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/health")
    public ResponseEntity<String> get() {
        return ResponseEntity.ok("NoteRAG is running");
    }
}
