package com.huanf.noterag.chunk;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "noterag.chunking.strategy", havingValue = "custom", matchIfMissing = true)
public class CustomMarkdownChunkingStrategy implements ChunkingStrategy {

    private final MarkdownChunkTransformer markdownChunkTransformer;

    public CustomMarkdownChunkingStrategy(MarkdownChunkTransformer markdownChunkTransformer) {
        this.markdownChunkTransformer = markdownChunkTransformer;
    }

    @Override
    public List<Document> transform(List<Document> documents) {
        return markdownChunkTransformer.transform(documents);
    }
}
