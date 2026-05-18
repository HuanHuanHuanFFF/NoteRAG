package com.huanf.noterag.prompt;

import com.huanf.noterag.rag.AnswerCitationExtractor;
import com.huanf.noterag.rag.CitationMarkers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerCitationExtractorTests {

    @Test
    void citationMarkerFormatUsesExpectedPrivateUseCharacters() {
        assertThat(CitationMarkers.format(1)).isEqualTo("\uE200cite\uE2021\uE201");
        assertThat(CitationMarkers.formatPlaceholder()).isEqualTo("\uE200cite\uE202sourceId\uE201");
    }

    @Test
    void extractsSingleCitation() {
        assertThat(AnswerCitationExtractor.extractSourceIds("answer" + CitationMarkers.format(140L)))
                .containsExactly(140L);
    }

    @Test
    void deduplicatesRepeatedCitations() {
        String answer = "first" + CitationMarkers.format(218L)
                + " second" + CitationMarkers.format(218L);

        assertThat(AnswerCitationExtractor.extractSourceIds(answer))
                .containsExactly(218L);
    }

    @Test
    void preservesFirstCitationOrder() {
        String answer = "third" + CitationMarkers.format(305L)
                + " first" + CitationMarkers.format(140L)
                + " third again" + CitationMarkers.format(305L);

        assertThat(AnswerCitationExtractor.extractSourceIds(answer))
                .containsExactly(305L, 140L);
    }

    @Test
    void returnsEmptyListWhenAnswerHasNoCitation() {
        assertThat(AnswerCitationExtractor.extractSourceIds("根据当前笔记内容无法确定。"))
                .isEmpty();
    }

    @Test
    void throwsWhenCitationSourceIdIsMalformed() {
        String malformedCitation = CitationMarkers.START
                + CitationMarkers.TYPE
                + CitationMarkers.SEPARATOR
                + "s1"
                + CitationMarkers.END;

        assertThatThrownBy(() -> AnswerCitationExtractor.extractSourceIds("answer" + malformedCitation))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
