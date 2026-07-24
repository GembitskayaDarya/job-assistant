package com.darya.jobassistant.vacancyimport.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class VacancyImportCleanupPropertiesTest {

    private static final Duration FIXED_DELAY = Duration.ofHours(1);
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 100;

    @Test
    void validConfiguration_isAccepted() {
        assertThatCode(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, BATCH_SIZE)).doesNotThrowAnyException();
    }

    @Test
    void disabledCleanup_doesNotRequireOtherFieldsToBeValid() {
        assertThatCode(() -> properties(false, null, null, 0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroFixedDelay() {
        assertThatThrownBy(() -> properties(true, Duration.ZERO, INITIAL_DELAY, BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFixedDelay() {
        assertThatThrownBy(() -> properties(true, Duration.ofMinutes(-1), INITIAL_DELAY, BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, Duration.ofSeconds(-1), BATCH_SIZE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsZeroInitialDelay() {
        assertThatCode(() -> properties(true, FIXED_DELAY, Duration.ZERO, BATCH_SIZE)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroBatchSize() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeBatchSize() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnreasonablyLargeBatchSize() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, 1_000_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBatchSizeOfOne() {
        assertThatCode(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, 1)).doesNotThrowAnyException();
    }

    private VacancyImportCleanupProperties properties(boolean enabled, Duration fixedDelay, Duration initialDelay, int batchSize) {
        return new VacancyImportCleanupProperties(enabled, fixedDelay, initialDelay, batchSize);
    }
}
