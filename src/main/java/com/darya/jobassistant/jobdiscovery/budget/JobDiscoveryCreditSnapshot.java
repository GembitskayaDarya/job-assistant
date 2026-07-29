package com.darya.jobassistant.jobdiscovery.budget;

import java.time.Instant;

/**
 * A validated, provider-neutral snapshot of a provider's credit account state, as fetched exactly
 * once by a {@link JobDiscoveryBudgetPort} implementation before a run starts. Deliberately does
 * not require {@code remainingCredits <= planCredits}: bonus/top-up credit behavior on the
 * provider's side must not make an otherwise valid snapshot unusable.
 */
public record JobDiscoveryCreditSnapshot(
        long remainingCredits,
        long planCredits,
        Instant billingPeriodStart,
        Instant billingPeriodEnd
) {

    public JobDiscoveryCreditSnapshot {
        if (remainingCredits < 0) {
            throw new IllegalArgumentException("remainingCredits must not be negative");
        }
        if (planCredits <= 0) {
            throw new IllegalArgumentException("planCredits must be positive");
        }
        if (billingPeriodStart == null) {
            throw new IllegalArgumentException("billingPeriodStart must not be null");
        }
        if (billingPeriodEnd == null) {
            throw new IllegalArgumentException("billingPeriodEnd must not be null");
        }
        if (!billingPeriodEnd.isAfter(billingPeriodStart)) {
            throw new IllegalArgumentException("billingPeriodEnd must be after billingPeriodStart");
        }
    }

    /** {@code max(0, planCredits - remainingCredits)} - never negative, see the class javadoc. */
    public long estimatedUsedCredits() {
        return Math.max(0, planCredits - remainingCredits);
    }
}
