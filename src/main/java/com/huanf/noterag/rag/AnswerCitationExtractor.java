package com.huanf.noterag.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class AnswerCitationExtractor {

    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("[1-9]\\d*");

    private AnswerCitationExtractor() {
    }

    /**
     * 提取 answer 中出现过的 citation sourceId。
     *
     * <p>无 citation 时返回空列表。若出现 marker 但 sourceId 不是正整数数字，
     * 抛出 {@link IllegalArgumentException}，由上层转换成 LLM 结果异常。</p>
     *
     * @param answer LLM 原始回答，允许为 null 或空
     * @return 按首次引用顺序去重后的 1-based sourceId 列表
     */
    public static List<Long> extractSourceIds(String answer) {
        if (answer == null || answer.isEmpty()) {
            return List.of();
        }

        Matcher matcher = CitationMarkers.markerPattern().matcher(answer);
        Set<Long> sourceIds = new LinkedHashSet<>();
        while (matcher.find()) {
            String rawSourceId = matcher.group(1);
            if (!SOURCE_ID_PATTERN.matcher(rawSourceId).matches()) {
                log.warn("Citation 解析发现非法 sourceId: '{}'", rawSourceId);
                throw new IllegalArgumentException("invalid citation source id: " + rawSourceId);
            }
            sourceIds.add(parseSourceId(rawSourceId));
        }
        log.debug("Citation 解析完成, extractedSourceIds={}", sourceIds);
        return List.copyOf(sourceIds);
    }

    /** 将已通过格式校验的 sourceId 文本转成 long，防御极大数字溢出。 */
    private static long parseSourceId(String rawSourceId) {
        try {
            return Long.parseLong(rawSourceId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid citation source id: " + rawSourceId, ex);
        }
    }
}
