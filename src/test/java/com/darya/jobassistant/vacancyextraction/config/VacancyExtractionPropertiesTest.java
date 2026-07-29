package com.darya.jobassistant.vacancyextraction.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VacancyExtractionPropertiesTest {

    @Test
    void validConfiguration_isAccepted() {
        assertThatCode(() -> new VacancyExtractionProperties(40_000, 10_000)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroMaxInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMaxInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveMaxInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(100_001, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsMaxInputCharsUpperBound() {
        assertThatCode(() -> new VacancyExtractionProperties(100_000, 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeTailInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(1000, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsZeroTailInputChars() {
        assertThatCode(() -> new VacancyExtractionProperties(1000, 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTailInputCharsEqualToMaxInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(1000, 1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTailInputCharsAboveMaxInputChars() {
        assertThatThrownBy(() -> new VacancyExtractionProperties(1000, 1001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTailInputCharsOneBelowMaxInputChars() {
        assertThatCode(() -> new VacancyExtractionProperties(1000, 999)).doesNotThrowAnyException();
    }
}
