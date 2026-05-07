package com.huanf.noterag.util;

public final class EstimatedTokenCounter {

    private EstimatedTokenCounter() {
    }

    /**
     * 直接返回文本的估算 token 数，适合写入文档或 chunk 的 metadata。
     */
    public static int estimate(String text) {
        return count(text).estimate();
    }

    /**
     * 扫描文本并返回估算 token 所需的分类计数。
     *
     * <p>当前估算规则：CJK 字符按 1 字符约 1 token；ASCII 字符按约 4 字符 1 token；
     * 其他非 ASCII、非 CJK 字符按约 2 字符 1 token；空白字符忽略。</p>
     *
     * <p>这里返回原始分类计数，而不是直接返回 token 数，是为了在 chunk 组装时先合并多个段落，
     * 再统一向上取整。否则很多英文短段落会因为逐段取整被高估。该方法是高频文本扫描路径，
     * 循环内只维护局部计数，避免逐字符创建对象。</p>
     */
    public static TokenCounts count(String text) {
        if (text == null || text.isEmpty()) {
            return TokenCounts.EMPTY;
        }

        int cjkCount = 0;
        int asciiCount = 0;
        int otherCount = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);

            switch (classify(codePoint)) {
                case CJK -> cjkCount++;
                case ASCII -> asciiCount++;
                case OTHER -> otherCount++;
                case IGNORED -> {
                }
            }
        }

        return new TokenCounts(cjkCount, asciiCount, otherCount);
    }

    /**
     * 按当前规则把分类计数转换为估算 token 数，供逐字符截取等高频路径复用。
     */
    public static int estimate(int cjkCount, int asciiCount, int otherCount) {
        return cjkCount + ceilDiv(asciiCount, 4) + ceilDiv(otherCount, 2);
    }

    /**
     * 返回 code point 在估算规则中的分类，空白字符当前不参与估算。
     */
    public static CodePointKind classify(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return CodePointKind.IGNORED;
        }
        if (codePoint <= 0x7F) {
            return CodePointKind.ASCII;
        }
        if (isCjk(codePoint)) {
            return CodePointKind.CJK;
        }
        return CodePointKind.OTHER;
    }

    /**
     * 判断 code point 是否属于当前按 1 字符约 1 token 处理的 CJK 范围。
     */
    private static boolean isCjk(int codePoint) {
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> true;
            default -> false;
        };
    }

    /**
     * 整数向上取整，用于 ASCII 约 4 字符 1 token、其他字符约 2 字符 1 token。
     */
    private static int ceilDiv(int value, int divisor) {
        if (value == 0) {
            return 0;
        }
        return (value + divisor - 1) / divisor;
    }

    public enum CodePointKind {
        IGNORED,
        CJK,
        ASCII,
        OTHER
    }

    /**
     * 估算 token 前的原始分类计数。
     *
     * <p>这是不可变值对象。chunk 组装时先合并分类计数，最后再调用 {@link #estimate()}，
     * 可以避免逐段估算导致的重复向上取整。</p>
     */
    public record TokenCounts(int cjkCount, int asciiCount, int otherCount) {

        public static final TokenCounts EMPTY = new TokenCounts(0, 0, 0);

        /**
         * 按当前规则把分类计数转换为估算 token 数。
         */
        public int estimate() {
            return EstimatedTokenCounter.estimate(cjkCount, asciiCount, otherCount);
        }

        /**
         * 段落/chunk 级合并另一段文本的分类计数，用于把多个段落组成一个 chunk 后再统一估算。
         */
        public TokenCounts plus(TokenCounts other) {
            if (other == null) {
                return this;
            }
            return new TokenCounts(
                    cjkCount + other.cjkCount,
                    asciiCount + other.asciiCount,
                    otherCount + other.otherCount);
        }
    }
}
