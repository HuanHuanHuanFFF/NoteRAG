package com.huanf.noterag.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstimatedTokenCounterTests {

    @Test
    void estimateCountsChineseCharactersAsTokens() {
        assertThat(EstimatedTokenCounter.estimate("你好世界")).isEqualTo(4);
    }

    @Test
    void estimateRoundsAsciiCharactersUpByFour() {
        assertThat(EstimatedTokenCounter.estimate("abcd")).isEqualTo(1);
        assertThat(EstimatedTokenCounter.estimate("abcde")).isEqualTo(2);
    }

    @Test
    void estimateCountsMixedChineseAndAsciiSeparately() {
        assertThat(EstimatedTokenCounter.estimate("你好abcde")).isEqualTo(4);
    }

    @Test
    void tokenCountsCanBeCombinedBeforeRounding() {
        EstimatedTokenCounter.TokenCounts counts = EstimatedTokenCounter.count("a")
                .plus(EstimatedTokenCounter.count("b"))
                .plus(EstimatedTokenCounter.count("c"))
                .plus(EstimatedTokenCounter.count("d"));

        assertThat(counts.estimate()).isEqualTo(1);
    }

    @Test
    void classifiedCountsCanBeEstimatedAfterCombining() {
        EstimatedTokenCounter.TokenCounts counts = new EstimatedTokenCounter.TokenCounts(0, 3, 1)
                .plus(new EstimatedTokenCounter.TokenCounts(0, 1, 1));

        assertThat(counts.estimate()).isEqualTo(2);
        assertThat(EstimatedTokenCounter.estimate(counts.cjkCount(), counts.asciiCount(), counts.otherCount()))
                .isEqualTo(2);
    }

    @Test
    void estimateRoundsOtherNonAsciiCharactersUpByTwo() {
        assertThat(EstimatedTokenCounter.estimate("éΩ😀")).isEqualTo(2);
    }

    @Test
    void estimateIgnoresWhitespace() {
        assertThat(EstimatedTokenCounter.estimate("a b\nc\t")).isEqualTo(1);
    }

    @Test
    void estimateReturnsZeroForNullOrBlank() {
        assertThat(EstimatedTokenCounter.estimate(null)).isZero();
        assertThat(EstimatedTokenCounter.estimate("")).isZero();
        assertThat(EstimatedTokenCounter.estimate(" \n\t")).isZero();
    }
}
