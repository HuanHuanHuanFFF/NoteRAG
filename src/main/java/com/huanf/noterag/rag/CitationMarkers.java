package com.huanf.noterag.rag;

import java.util.regex.Pattern;

/**
 * NoteRAG 内部 citation marker 约定。
 *
 * <p>LLM 在回答中通过这组私有区字符标记实际使用过的 source，例如：
 * {@code \uE200cite\uE2021\uE201}。后端据此过滤返回给前端的 sources。
 * 这里集中维护格式，避免 prompt、解析器和测试散落硬编码。</p>
 */
public final class CitationMarkers {

    /** citation marker 起始字符，使用 Unicode 私有区以降低和正文冲突的概率。 */
    public static final String START = "\uE200";

    /** marker 类型，目前只支持 cite。 */
    public static final String TYPE = "cite";

    /** marker 类型和 sourceId 之间的分隔字符。 */
    public static final String SEPARATOR = "\uE202";

    /** citation marker 结束字符。 */
    public static final String END = "\uE201";

    private static final String PREFIX = START + TYPE + SEPARATOR;
    private static final Pattern MARKER_PATTERN = Pattern.compile(
            Pattern.quote(PREFIX) + "(.*?)" + Pattern.quote(END));

    private CitationMarkers() {
    }

    /**
     * 生成可被 LLM 输出并被后端解析的 citation marker。
     *
     * @param sourceId prompt 中的 1-based sourceId
     * @return 完整 citation marker，例如 {@code \uE200cite\uE2021\uE201}
     */
    public static String format(long sourceId) {
        if (sourceId <= 0) {
            throw new IllegalArgumentException("sourceId must be positive");
        }
        return PREFIX + sourceId + END;
    }

    /**
     * 生成写入系统提示词的格式占位说明。
     *
     * <p>这里保留 sourceId 文本，便于 LLM 理解需要替换成候选片段中的数字 id。</p>
     */
    public static String formatPlaceholder() {
        return PREFIX + "sourceId" + END;
    }

    /** 返回 marker 扫描用正则，只在同包解析器中使用。 */
    static Pattern markerPattern() {
        return MARKER_PATTERN;
    }
}
