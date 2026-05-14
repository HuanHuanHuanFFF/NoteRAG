package com.huanf.noterag.chunk;

import java.util.List;

import org.springframework.ai.document.Document;

public interface ChunkingStrategy {

    List<Document> transform(List<Document> documents);
}
