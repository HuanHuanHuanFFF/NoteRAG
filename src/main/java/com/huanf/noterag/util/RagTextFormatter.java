package com.huanf.noterag.util;

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

    public static String formatQueryText(String question) {
        return stripToEmpty(question);
    }

    private static String stripToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
