package com.darya.jobassistant.jobdiscovery.budget;

import java.time.Instant;

/**
 * Immutable, sanitized outcome of one pre-run budget assessment - safe to log and to embed
 * verbatim in {@code JobDiscoveryRunResult}. Never carries a raw provider error body, API key,
 * Authorization header, or Firecrawl-specific type; {@link #reasonCategory()} is a short,
 * low-cardinality, content-free explanation (e.g. {@code "HTTP_429"}, {@code "TIMEOUT"}), never a
 * raw exception message.
 *
 * <p>{@link #remainingCredits()}, {@link #planCredits()}, {@link #estimatedUsedCredits()}, {@link
 * #billingPeriodStart()}, and {@link #billingPeriodEnd()} are only ever non-null when {@link
 * #status()} is backed by a verified {@link JobDiscoveryCreditSnapshot} (today, only {@link
 * JobDiscoveryBudgetStatus#ALLOWED} and the three {@code DENIED_*} statuses) - {@link
 * JobDiscoveryBudgetStatus#NOT_REQUIRED} and {@link JobDiscoveryBudgetStatus#UNAVAILABLE} never
 * reached a real provider snapshot, so those fields stay {@code null}.
 */
public record JobDiscoveryBudgetDecision(
        JobDiscoveryBudgetStatus status,
        boolean allowed,
        long estimatedSearchCredits,
        long estimatedScrapeCredits,
        long estimatedTotalCredits,
        long configuredMonthlyLimit,
        long configuredReserveCredits,
        long configuredPerRunLimit,
        Long remainingCredits,
        Long planCredits,
        Long estimatedUsedCredits,
        Instant billingPeriodStart,
        Instant billingPeriodEnd,
        String reasonCategory
) {

    public JobDiscoveryBudgetDecision {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    /** The query planner produced zero executable queries - the budget port is never called. */
    public static JobDiscoveryBudgetDecision notRequired(long monthlyLimit, long reserveCredits, long perRunLimit) {
        return new JobDiscoveryBudgetDecision(JobDiscoveryBudgetStatus.NOT_REQUIRED, false,
                0, 0, 0, monthlyLimit, reserveCredits, perRunLimit,
                null, null, null, null, null, null);
    }

    /** The provider's credit balance could not be verified - the run must be denied (fail-closed). */
    public static JobDiscoveryBudgetDecision unavailable(long estimatedSearchCredits, long estimatedScrapeCredits,
                                                           long monthlyLimit, long reserveCredits, long perRunLimit,
                                                           String reasonCategory) {
        long totalCredits = Math.addExact(estimatedSearchCredits, estimatedScrapeCredits);
        return new JobDiscoveryBudgetDecision(JobDiscoveryBudgetStatus.UNAVAILABLE, false,
                estimatedSearchCredits, estimatedScrapeCredits, totalCredits,
                monthlyLimit, reserveCredits, perRunLimit,
                null, null, null, null, null, reasonCategory);
    }

    /**
     * Built only from a verified {@link JobDiscoveryCreditSnapshot} by {@link
     * JobDiscoveryBudgetPolicy} - {@code status} must be {@link JobDiscoveryBudgetStatus#ALLOWED}
     * or one of the {@code DENIED_*} statuses, never {@link JobDiscoveryBudgetStatus#NOT_REQUIRED}
     * or {@link JobDiscoveryBudgetStatus#UNAVAILABLE}.
     */
    static JobDiscoveryBudgetDecision decided(JobDiscoveryBudgetStatus status, long estimatedSearchCredits,
                                               long estimatedScrapeCredits, long estimatedTotalCredits,
                                               long monthlyLimit, long reserveCredits, long perRunLimit,
                                               JobDiscoveryCreditSnapshot snapshot) {
        return new JobDiscoveryBudgetDecision(status, status == JobDiscoveryBudgetStatus.ALLOWED,
                estimatedSearchCredits, estimatedScrapeCredits, estimatedTotalCredits,
                monthlyLimit, reserveCredits, perRunLimit,
                snapshot.remainingCredits(), snapshot.planCredits(), snapshot.estimatedUsedCredits(),
                snapshot.billingPeriodStart(), snapshot.billingPeriodEnd(), null);
    }
}
