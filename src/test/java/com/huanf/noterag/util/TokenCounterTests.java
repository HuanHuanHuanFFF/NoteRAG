package com.huanf.noterag.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenCounterTests {

    @Test
    void countReturnsZeroForBlankText() {
        assertThat(TokenCounter.count(null)).isZero();
        assertThat(TokenCounter.count("")).isZero();
        assertThat(TokenCounter.count(" \n\t")).isZero();
    }

    @Test
    void countReturnsStablePositiveCountForSimpleText() {
        String text = "MySQL MVCC 依赖 undo log 和 ReadView。";

        int first = TokenCounter.count(text);
        int second = TokenCounter.count(text);

        assertThat(first).isPositive();
        assertThat(second).isEqualTo(first);
    }
}
