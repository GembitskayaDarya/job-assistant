package com.darya.jobassistant.notifications.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobNotificationCandidateQueryAdapterValidationTest {

    @Test
    void validParameters_areAccepted() {
        assertThatCode(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(123L, 50, 10))
                .doesNotThrowAnyException();
    }

    @Test
    void nullRecipientChatId_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(null, 50, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroRecipientChatId_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(0L, 50, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreBelowZero_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(123L, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreAbove100_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(123L, 101, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroLimit_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(123L, 50, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeLimit_isRejected() {
        assertThatThrownBy(() -> JobNotificationCandidateQueryAdapter.validateQueryParameters(123L, 50, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
