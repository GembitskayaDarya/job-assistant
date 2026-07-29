package com.darya.jobassistant.jobdiscovery.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobDiscoveryCreditSnapshotTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-31T23:59:59Z");

    @Test
    void validSnapshot_isAccepted() {
        assertThatCode(() -> new JobDiscoveryCreditSnapshot(1000, 1000, START, END)).doesNotThrowAnyException();
    }

    @Test
    void zeroRemainingCredits_isValid() {
        assertThatCode(() -> new JobDiscoveryCreditSnapshot(0, 1000, START, END)).doesNotThrowAnyException();
    }

    @Test
    void negativeRemainingCredits_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(-1, 1000, START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remainingCreditsGreaterThanPlanCredits_isAccepted() {
        assertThatCode(() -> new JobDiscoveryCreditSnapshot(1500, 1000, START, END)).doesNotThrowAnyException();
    }

    @Test
    void zeroPlanCredits_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(0, 0, START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePlanCredits_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(0, -1, START, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBillingPeriodStart_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(1000, 1000, null, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBillingPeriodEnd_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(1000, 1000, START, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void billingPeriodEndBeforeStart_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(1000, 1000, END, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void billingPeriodEndEqualToStart_isRejected() {
        assertThatThrownBy(() -> new JobDiscoveryCreditSnapshot(1000, 1000, START, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void estimatedUsedCredits_isPlanMinusRemaining() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(700, 1000, START, END);

        assertThat(snapshot.estimatedUsedCredits()).isEqualTo(300);
    }

    @Test
    void estimatedUsedCredits_neverNegative_whenRemainingExceedsPlan() {
        JobDiscoveryCreditSnapshot snapshot = new JobDiscoveryCreditSnapshot(1500, 1000, START, END);

        assertThat(snapshot.estimatedUsedCredits()).isZero();
    }
}
