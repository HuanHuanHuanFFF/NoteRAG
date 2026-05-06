package com.huanf.noterag.util;

public final class EstimatedTokenCounter {

    private EstimatedTokenCounter() {
    }

    public static int estimate(String text) {
        return count(text).estimate();
    }

    public static TokenCounts count(String text) {
        if (text == null || text.isBlank()) {
            return TokenCounts.EMPTY;
        }

        TokenCounts counts = TokenCounts.EMPTY;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            counts = counts.plus(codePoint);
        }

        return counts;
    }

    private static boolean isCjk(int codePoint) {
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> true;
            default -> false;
        };
    }

    private static int ceilDiv(int value, int divisor) {
        if (value == 0) {
            return 0;
        }
        return (value + divisor - 1) / divisor;
    }

    public record TokenCounts(int cjkCount, int asciiCount, int otherCount) {

        public static final TokenCounts EMPTY = new TokenCounts(0, 0, 0);

        public int estimate() {
            return cjkCount + ceilDiv(asciiCount, 4) + ceilDiv(otherCount, 2);
        }

        public TokenCounts plus(TokenCounts other) {
            return new TokenCounts(
                    cjkCount + other.cjkCount,
                    asciiCount + other.asciiCount,
                    otherCount + other.otherCount);
        }

        public TokenCounts plus(int codePoint) {
            if (Character.isWhitespace(codePoint)) {
                return this;
            }
            if (isCjk(codePoint)) {
                return new TokenCounts(cjkCount + 1, asciiCount, otherCount);
            }
            if (codePoint <= 0x7F) {
                return new TokenCounts(cjkCount, asciiCount + 1, otherCount);
            }
            return new TokenCounts(cjkCount, asciiCount, otherCount + 1);
        }
    }
}
