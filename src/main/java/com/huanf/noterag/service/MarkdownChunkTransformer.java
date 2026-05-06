package com.huanf.noterag.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownChunkTransformer implements DocumentTransformer {

    public static final int MIN_TARGET_CHARS = 300;
    public static final int MAX_TARGET_CHARS = 800;
    public static final int OVERLAP_CHARS = 80;

    private static final Pattern ATX_HEADING = Pattern.compile("^[ \\t]{0,3}(#{1,6})[ \\t]+(.+?)[ \\t]*$");
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String HEADING_SEPARATOR = " > ";
    private static final String EMPTY_HEADING_PATH = "";

    /**
     * Spring AI 的转换入口，统一转调 {@link #apply(List)}，避免两种调用方式行为不一致。
     */
    @Override
    public List<Document> transform(List<Document> documents) {
        return apply(documents);
    }

    /**
     * 将输入的 Spring AI Document 切成多个 chunk Document，并保留原 metadata。
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }

            for (Section section : parseSections(document.getText())) {
                chunkIndex = appendSectionChunks(document, section, chunkIndex, chunks);
            }
        }

        return chunks;
    }

    /**
     * 将 Markdown 解析为按标题路径分组的 section，每个 section 保存标题路径和段落列表。
     */
    private List<Section> parseSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String[] headingStack = new String[6];
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String headingPath = EMPTY_HEADING_PATH;

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            Matcher heading = ATX_HEADING.matcher(line);
            if (heading.matches()) {
                flushParagraph(paragraph, paragraphs);
                addSection(sections, headingPath, paragraphs);
                paragraphs = new ArrayList<>();
                headingPath = updateHeadingPath(headingStack, heading);
                continue;
            }

            if (line.isBlank()) {
                flushParagraph(paragraph, paragraphs);
                continue;
            }

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line.stripTrailing());
        }

        flushParagraph(paragraph, paragraphs);
        addSection(sections, headingPath, paragraphs);
        return sections;
    }

    /**
     * 将一个标题 section 转换成一个或多个 chunk，并沿用全局递增的 chunkIndex。
     */
    private int appendSectionChunks(Document source, Section section, int chunkIndex, List<Document> chunks) {
        StringBuilder current = new StringBuilder();
        boolean hasBodyText = false;

        for (String paragraph : section.paragraphs()) {
            String remaining = paragraph.strip();
            if (remaining.isEmpty()) {
                continue;
            }

            if (hasBodyText && wouldExceedMax(current, remaining)) {
                chunkIndex = emitChunk(source, section.headingPath(), current, chunkIndex, chunks, true);
                hasBodyText = false;
            }

            while (!remaining.isEmpty()) {
                int separatorLength = current.isEmpty() ? 0 : PARAGRAPH_SEPARATOR.length();
                int available = MAX_TARGET_CHARS - current.length() - separatorLength;

                if (available <= 0) {
                    chunkIndex = emitChunk(source, section.headingPath(), current, chunkIndex, chunks, true);
                    hasBodyText = false;
                    continue;
                }

                if (remaining.length() <= available) {
                    appendParagraph(current, remaining);
                    hasBodyText = true;
                    remaining = "";
                } else {
                    String piece = remaining.substring(0, available).stripTrailing();
                    if (piece.isEmpty()) {
                        piece = remaining.substring(0, available);
                    }
                    appendParagraph(current, piece);
                    chunkIndex = emitChunk(source, section.headingPath(), current, chunkIndex, chunks, true);
                    hasBodyText = false;
                    remaining = remaining.substring(piece.length()).stripLeading();
                }
            }
        }

        if (hasBodyText && !current.isEmpty()) {
            chunkIndex = emitChunk(source, section.headingPath(), current, chunkIndex, chunks, false);
        }

        return chunkIndex;
    }

    /**
     * 判断把段落追加到当前缓冲区后是否会超过 chunk 最大长度。
     */
    private boolean wouldExceedMax(StringBuilder current, String paragraph) {
        if (current.isEmpty()) {
            return paragraph.length() > MAX_TARGET_CHARS;
        }
        return current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length() > MAX_TARGET_CHARS;
    }

    /**
     * 将当前缓冲区输出为 Spring AI Document，并按需把右侧 overlap 写回缓冲区。
     */
    private int emitChunk(
            Document source,
            String headingPath,
            StringBuilder current,
            int chunkIndex,
            List<Document> chunks,
            boolean keepOverlap) {
        String chunkText = current.toString().strip();
        if (chunkText.isEmpty()) {
            current.setLength(0);
            return chunkIndex;
        }

        chunks.add(new Document(chunkText, buildMetadata(source, headingPath, chunkIndex, chunkText.length())));
        current.setLength(0);

        if (keepOverlap) {
            current.append(rightOverlap(chunkText));
        }

        return chunkIndex + 1;
    }

    /**
     * 构建输出 metadata：先复制源 metadata，再写入 chunk 相关字段。
     */
    private Map<String, Object> buildMetadata(Document source, String headingPath, int chunkIndex, int charCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("headingPath", headingPath);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("charCount", charCount);
        return metadata;
    }

    /**
     * 将段落追加到 chunk 缓冲区，段落之间使用 Markdown 空行分隔。
     */
    private void appendParagraph(StringBuilder current, String paragraph) {
        if (!current.isEmpty()) {
            current.append(PARAGRAPH_SEPARATOR);
        }
        current.append(paragraph);
    }

    /**
     * 如果当前段落缓冲区非空，则写入段落列表并清空缓冲区。
     */
    private void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        String text = paragraph.toString().strip();
        if (!text.isEmpty()) {
            paragraphs.add(text);
        }
        paragraph.setLength(0);
    }

    /**
     * 只添加包含正文段落的 section；没有正文的空标题不会生成 chunk。
     */
    private void addSection(List<Section> sections, String headingPath, List<String> paragraphs) {
        if (!paragraphs.isEmpty()) {
            sections.add(new Section(headingPath, paragraphs));
        }
    }

    /**
     * 根据 ATX 标题维护标题栈，并返回当前标题路径。
     */
    private String updateHeadingPath(String[] headingStack, Matcher heading) {
        int level = heading.group(1).length();
        headingStack[level - 1] = cleanHeadingText(heading.group(2));
        Arrays.fill(headingStack, level, headingStack.length, null);
        return buildHeadingPath(headingStack);
    }

    /**
     * 清理标题文本，去掉 Markdown 允许的尾部 # 标记。
     */
    private String cleanHeadingText(String headingText) {
        return headingText.replaceFirst("[ \\t]+#+[ \\t]*$", "").strip();
    }

    /**
     * 根据当前标题栈生成可读的标题路径。
     */
    private String buildHeadingPath(String[] headingStack) {
        List<String> path = new ArrayList<>();
        for (String heading : headingStack) {
            if (heading != null && !heading.isBlank()) {
                path.add(heading);
            }
        }
        return path.isEmpty() ? EMPTY_HEADING_PATH : String.join(HEADING_SEPARATOR, path);
    }

    /**
     * 取当前 chunk 右侧文本，作为下一个 chunk 的 overlap。
     */
    private String rightOverlap(String text) {
        if (text.length() <= OVERLAP_CHARS) {
            return text;
        }
        return text.substring(text.length() - OVERLAP_CHARS);
    }

    /**
     * Markdown 解析后的 section：标题路径和该标题下的段落列表。
     */
    private record Section(String headingPath, List<String> paragraphs) {
    }
}
