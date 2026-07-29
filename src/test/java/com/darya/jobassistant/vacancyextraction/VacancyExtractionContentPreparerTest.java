package com.darya.jobassistant.vacancyextraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.vacancyextraction.config.VacancyExtractionProperties;
import com.darya.jobassistant.vacancyextraction.model.PreparedVacancyContent;
import org.junit.jupiter.api.Test;

class VacancyExtractionContentPreparerTest {

    private static final String OMISSION_MARKER = "[CONTENT OMITTED FOR LENGTH LIMIT]";

    @Test
    void contentBelowLimit_isPreservedExactly() {
        VacancyExtractionContentPreparer preparer = preparer(100, 20);
        String content = "Short vacancy description.";

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.truncated()).isFalse();
        assertThat(result.originalCharCount()).isEqualTo(content.length());
        assertThat(result.preparedCharCount()).isEqualTo(content.length());
    }

    @Test
    void contentExactlyAtLimit_isPreservedExactly() {
        VacancyExtractionContentPreparer preparer = preparer(20, 5);
        String content = "x".repeat(20);

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void oversizedContent_retainsDeterministicHeadAndTail() {
        VacancyExtractionContentPreparer preparer = preparer(30, 10);
        String content = "H".repeat(15) + "M".repeat(20) + "T".repeat(15);

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.truncated()).isTrue();
        assertThat(result.content()).startsWith("H".repeat(15) + "M".repeat(5));
        assertThat(result.content()).endsWith("T".repeat(10));
        assertThat(result.originalCharCount()).isEqualTo(content.length());
    }

    @Test
    void oversizedContent_insertsStableOmissionMarkerExactlyOnce() {
        VacancyExtractionContentPreparer preparer = preparer(30, 10);
        String content = "A".repeat(100);

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.content()).contains(OMISSION_MARKER);
        int firstIndex = result.content().indexOf(OMISSION_MARKER);
        int lastIndex = result.content().lastIndexOf(OMISSION_MARKER);
        assertThat(firstIndex).isEqualTo(lastIndex);
    }

    @Test
    void oversizedContent_doesNotSplitSurrogatePairAtHeadBoundary() {
        // U+1F600 GRINNING FACE is a surrogate pair, placed exactly straddling the head cut point:
        // with maxInputChars=26 and tailInputChars=5, headChars=21, so the naive cut at index 21
        // would fall between the emoji's high surrogate (index 20) and low surrogate (index 21).
        String emoji = "😀";
        VacancyExtractionContentPreparer preparer = preparer(26, 5);
        String content = "H".repeat(20) + emoji + "T".repeat(20);

        PreparedVacancyContent result = preparer.prepare(content);

        String head = result.content().substring(0, result.content().indexOf('\n'));
        assertThat(head).doesNotContain("\uD83D").doesNotContain("\uDE00");
        assertThat(head).isEqualTo("H".repeat(20));
    }

    @Test
    void oversizedContent_doesNotSplitSurrogatePairAtTailBoundary() {
        // With tailInputChars=5 and this 26-character content, the naive tail cut at
        // length-5=21 would fall between the emoji's high surrogate (index 20, excluded) and low
        // surrogate (index 21, included).
        String emoji = "😀";
        VacancyExtractionContentPreparer preparer = preparer(25, 5);
        String content = "H".repeat(20) + emoji + "T".repeat(4);

        PreparedVacancyContent result = preparer.prepare(content);

        String tail = result.content().substring(result.content().lastIndexOf('\n') + 1);
        // The pair must appear whole in the tail (never split), even if that makes the tail one
        // character longer than the nominal tailInputChars.
        assertThat(tail).contains(emoji);
        assertThat(tail).doesNotStartWith("\uDE00");
    }

    @Test
    void tailInputCharsZero_isSupported() {
        VacancyExtractionContentPreparer preparer = preparer(10, 0);
        String content = "A".repeat(50);

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.truncated()).isTrue();
        assertThat(result.content()).isEqualTo("A".repeat(10) + "\n\n[CONTENT OMITTED FOR LENGTH LIMIT]\n\n");
    }

    @Test
    void preparation_reportsAccurateCharCountsForObservability() {
        VacancyExtractionContentPreparer preparer = preparer(30, 10);
        String content = "A".repeat(100);

        PreparedVacancyContent result = preparer.prepare(content);

        assertThat(result.originalCharCount()).isEqualTo(100);
        assertThat(result.preparedCharCount()).isEqualTo(result.content().length());
        assertThat(result.preparedCharCount()).isLessThan(result.originalCharCount());
    }

    private VacancyExtractionContentPreparer preparer(int maxInputChars, int tailInputChars) {
        return new VacancyExtractionContentPreparer(new VacancyExtractionProperties(maxInputChars, tailInputChars));
    }
}
