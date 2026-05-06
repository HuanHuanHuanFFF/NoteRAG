package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MarkdownChunkTransformerTests {

    private final MarkdownChunkTransformer transformer = new MarkdownChunkTransformer();

    @Test
    void transformKeepsHeadingPathMetadata() {
        Document source = new Document("""
                # Java

                Java notes.

                ## Collections

                HashMap notes.
                """);

        List<Document> chunks = transformer.transform(List.of(source));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("chunkIndex", 0)
                .containsEntry("charCount", "Java notes.".length());
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Collections")
                .containsEntry("chunkIndex", 1)
                .containsEntry("charCount", "HashMap notes.".length());
    }

    @Test
    void transformDoesNotEmitOverlapOnlyChunk() {
        Document source = new Document("""
                # Long Section

                %s

                %s
                """.formatted("a".repeat(790), "b".repeat(750)));

        List<Document> chunks = transformer.transform(List.of(source));

        assertThat(chunks).hasSize(3);
        assertThat(chunks)
                .extracting(Document::getText)
                .noneMatch(text -> text.length() == MarkdownChunkTransformer.OVERLAP_CHARS);
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata())
                        .containsEntry("headingPath", "Long Section"));
    }
}
