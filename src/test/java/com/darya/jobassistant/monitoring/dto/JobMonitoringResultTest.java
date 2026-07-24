package com.darya.jobassistant.monitoring.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JobMonitoringResultTest {

    @Test
    void createsResultWithValidCounters() {
        JobMonitoringResult result = new JobMonitoringResult(10, 4, 4, 2, 2, 0);

        assertThat(result.fetchedCount()).isEqualTo(10);
        assertThat(result.persistedCount()).isEqualTo(4);
        assertThat(result.analyzedCount()).isEqualTo(4);
        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.notifiedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void emptyFactoryReturnsAllZeroCounters() {
        JobMonitoringResult result = JobMonitoringResult.empty();

        assertThat(result.fetchedCount()).isZero();
        assertThat(result.persistedCount()).isZero();
        assertThat(result.analyzedCount()).isZero();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.notifiedCount()).isZero();
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void rejectsNegativeFetchedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(-1, 0, 0, 0, 0, 0));
    }

    @Test
    void rejectsNegativePersistedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(0, -1, 0, 0, 0, 0));
    }

    @Test
    void rejectsNegativeAnalyzedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(0, 0, -1, 0, 0, 0));
    }

    @Test
    void rejectsNegativeMatchedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(0, 0, 0, -1, 0, 0));
    }

    @Test
    void rejectsNegativeNotifiedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(0, 0, 0, 0, -1, 0));
    }

    @Test
    void rejectsNegativeFailedCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringResult(0, 0, 0, 0, 0, -1));
    }
}
