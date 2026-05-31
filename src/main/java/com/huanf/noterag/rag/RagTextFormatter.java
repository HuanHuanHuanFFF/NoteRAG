package com.huanf.noterag.rag;

public final class RagTextFormatter {

    private RagTextFormatter() {
    }

    public static String formatChunkContext(String title, String headingPath, String content) {
        String normalizedTitle = stripToEmpty(title);
        String normalizedHeadingPath = stripToEmpty(headingPath);
        String normalizedContent = content == null ? "" : content;

        if (normalizedHeadingPath.isEmpty()) {
            return """
                    文档标题: %s

                    正文:
                    %s""".formatted(normalizedTitle, normalizedContent);
        }

        return """
                文档标题: %s
                章节路径: %s

                正文:
                %s""".formatted(normalizedTitle, normalizedHeadingPath, normalizedContent);
    }

    public static String formatSummaryChunkContext(String title, String summary) {
        String normalizedTitle = stripToEmpty(title);
        String normalizedSummary = summary == null ? "" : summary;

        return """
                文档标题: %s
                内容类型: 笔记全文摘要
                适用问题: 这篇笔记主要讲了什么？总结一下这篇笔记。这个笔记的核心内容是什么？

                正文:
                %s""".formatted(normalizedTitle, normalizedSummary);
    }

    public static String formatQueryText(String question) {
        return stripToEmpty(question);
    }

    private static String stripToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
