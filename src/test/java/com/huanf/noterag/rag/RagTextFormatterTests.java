package com.huanf.noterag.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RagTextFormatterTests {

    @Test
    void formatChunkContextIncludesTitleHeadingPathAndContent() {
        String chunkContext = RagTextFormatter.formatChunkContext(
                " Java Guide ",
                " Java > Collections ",
                "HashMap notes.");

        assertThat(chunkContext).isEqualTo("""
                文档标题: Java Guide
                章节路径: Java > Collections

                正文:
                HashMap notes.""");
    }

    @Test
    void formatChunkContextOmitsBlankHeadingPath() {
        String chunkContext = RagTextFormatter.formatChunkContext(
                "Java Guide",
                "   ",
                "Java notes.");

        assertThat(chunkContext).isEqualTo("""
                文档标题: Java Guide

                正文:
                Java notes.""");
        assertThat(chunkContext)
                .doesNotContain("章节路径:")
                .doesNotContain("null");
    }

    @Test
    void formatQueryTextStripsQuestion() {
        assertThat(RagTextFormatter.formatQueryText("  What is RAG?\n"))
                .isEqualTo("What is RAG?");
    }

    @Test
    void formatQueryTextReturnsEmptyStringForNull() {
        assertThat(RagTextFormatter.formatQueryText(null)).isEmpty();
    }
}
