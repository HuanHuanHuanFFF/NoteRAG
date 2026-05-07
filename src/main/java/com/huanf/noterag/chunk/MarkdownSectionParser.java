package com.huanf.noterag.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 将 Markdown 文本解析为按标题路径分组的 section。
 *
 * <p>本类只负责 Markdown 结构识别：ATX 标题、headingPath、自然段、fenced code block。
 * 不负责 token 估算、overlap 或 Spring AI Document 构造。</p>
 */
@Component
final class MarkdownSectionParser {

    private static final Pattern ATX_HEADING = Pattern.compile("^[ \\t]{0,3}(#{1,6})[ \\t]+(.+?)[ \\t]*$");
    private static final Pattern FENCED_CODE_BLOCK_OPENING = Pattern.compile("^[ \\t]{0,3}(`{3,}|~{3,}).*$");
    private static final Pattern FENCED_CODE_BLOCK_CLOSING = Pattern.compile("^[ \\t]{0,3}(`{3,}|~{3,})[ \\t]*$");
    private static final String HEADING_SEPARATOR = " > ";
    private static final String EMPTY_HEADING_PATH = "";

    /**
     * 解析 Markdown，返回包含 headingPath 和段落列表的 section。
     */
    List<MarkdownSection> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<MarkdownSection> sections = new ArrayList<>();
        String[] headingStack = new String[6];
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String headingPath = EMPTY_HEADING_PATH;
        String activeFence = null;

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            activeFence = updateActiveFence(activeFence, line);

            Matcher heading = ATX_HEADING.matcher(line);
            if (activeFence == null && heading.matches()) {
                flushParagraph(paragraph, paragraphs);
                addSection(sections, headingPath, paragraphs);
                paragraphs = new ArrayList<>();
                headingPath = updateHeadingPath(headingStack, heading);
                continue;
            }

            if (line.isBlank() && activeFence == null) {
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
     * 当前段落缓冲区非空时写入段落列表，并清空缓冲区。
     */
    private void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        String text = paragraph.toString().strip();
        if (!text.isEmpty()) {
            paragraphs.add(text);
        }
        paragraph.setLength(0);
    }

    /**
     * 只保存包含正文段落的 section，避免空标题生成空 chunk。
     */
    private void addSection(List<MarkdownSection> sections, String headingPath, List<String> paragraphs) {
        if (!paragraphs.isEmpty()) {
            sections.add(new MarkdownSection(headingPath, paragraphs));
        }
    }

    /**
     * 维护 fenced code block 状态。
     *
     * <p>opening fence 支持 ``` 和 ~~~；closing fence 必须类型相同，且长度不短于 opening fence。</p>
     */
    private String updateActiveFence(String activeFence, String line) {
        Matcher fence = (activeFence == null ? FENCED_CODE_BLOCK_OPENING : FENCED_CODE_BLOCK_CLOSING).matcher(line);
        if (!fence.matches()) {
            return activeFence;
        }

        String marker = fence.group(1);
        if (activeFence == null) {
            return marker;
        }

        char activeFenceChar = activeFence.charAt(0);
        if (marker.charAt(0) == activeFenceChar && marker.length() >= activeFence.length()) {
            return null;
        }
        return activeFence;
    }

    /**
     * 根据 ATX 标题层级更新标题栈，并返回当前 headingPath。
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
     * 将标题栈转换为用于 metadata 的可读 headingPath。
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
}

/**
 * Markdown 解析后的最小结构单元：一个标题路径及其下属自然段。
 */
record MarkdownSection(String headingPath, List<String> paragraphs) {
}
