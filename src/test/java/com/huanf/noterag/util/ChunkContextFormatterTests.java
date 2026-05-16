package com.huanf.noterag.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkContextFormatterTests {

    @Test
    void formatChunkForEmbeddingIncludesTitleHeadingPathAndContent() {
        String embeddingText = ChunkContextFormatter.formatChunkForEmbedding(
                " Java Guide ",
                " Java > Collections ",
                "HashMap notes.");

        assertThat(embeddingText).isEqualTo("""
                文档标题: Java Guide
                章节路径: Java > Collections

                正文:
                HashMap notes.""");
    }

    @Test
    void formatChunkForEmbeddingOmitsBlankHeadingPath() {
        String embeddingText = ChunkContextFormatter.formatChunkForEmbedding(
                "Java Guide",
                "   ",
                "Java notes.");

        assertThat(embeddingText).isEqualTo("""
                文档标题: Java Guide

                正文:
                Java notes.""");
        assertThat(embeddingText)
                .doesNotContain("章节路径:")
                .doesNotContain("null");
    }

    @Test
    void formatQueryForEmbeddingStripsQuestion() {
        assertThat(ChunkContextFormatter.formatQueryForEmbedding("  What is RAG?\n"))
                .isEqualTo("What is RAG?");
    }

    @Test
    void formatQueryForEmbeddingReturnsEmptyStringForNull() {
        assertThat(ChunkContextFormatter.formatQueryForEmbedding(null)).isEmpty();
    }
}
