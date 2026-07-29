package com.darya.jobassistant.jobdiscovery.budget;

import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;

/**
 * Pure, provider-neutral spend policy applied to an already-estimated run cost and an already
 * -verified {@link JobDiscoveryCreditSnapshot}. Deliberately knows nothing about Firecrawl, HTTP,
 * or how the estimate/snapshot were obtained - only {@code FirecrawlJobDiscoveryBudgetAdapter}
 * calls this, and only after a provider snapshot has already been fetched and validated; a failed
 * or missing snapshot never reaches this class (see {@link JobDiscoveryBudgetDecision#unavailable}
 * for that path instead).
 *
 * <p>Decision precedence is fixed and deterministic:
 * <ol>
 *   <li>the estimated run cost exceeds the configured per-run limit -&gt; {@link
 *       JobDiscoveryBudgetStatus#DENIED_PER_RUN_LIMIT};
 *   <li>otherwise, the projected monthly usage (already-used + this run's estimate) would exceed
 *       the configured monthly limit -&gt; {@link JobDiscoveryBudgetStatus#DENIED_MONTHLY_LIMIT};
 *   <li>otherwise, spending the estimate would leave less than the configured reserve remaining
 *       -&gt; {@link JobDiscoveryBudgetStatus#DENIED_RESERVE_LIMIT};
 *   <li>otherwise -&gt; {@link JobDiscoveryBudgetStatus#ALLOWED}.
 * </ol>
 * A run is denied on the <em>first</em> limit it would cross, in this order, even if it would also
 * cross a later one - callers must not infer that only the reported limit was exceeded.
 */
public final class JobDiscoveryBudgetPolicy {

    private JobDiscoveryBudgetPolicy() {
    }

    public static JobDiscoveryBudgetDecision decide(long estimatedSearchCredits, long estimatedScrapeCredits,
                                                      long estimatedTotalCredits, JobDiscoveryProperties.Budget budget,
                                                      JobDiscoveryCreditSnapshot snapshot) {
        JobDiscoveryBudgetStatus status = evaluate(estimatedTotalCredits, budget, snapshot);
        return JobDiscoveryBudgetDecision.decided(status, estimatedSearchCredits, estimatedScrapeCredits,
                estimatedTotalCredits, budget.monthlyCreditLimit(), budget.reserveCredits(),
                budget.maxEstimatedCreditsPerRun(), snapshot);
    }

    private static JobDiscoveryBudgetStatus evaluate(long estimatedTotalCredits, JobDiscoveryProperties.Budget budget,
                                                       JobDiscoveryCreditSnapshot snapshot) {
        if (estimatedTotalCredits > budget.maxEstimatedCreditsPerRun()) {
            return JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT;
        }

        long projectedMonthlyUsage = Math.addExact(snapshot.estimatedUsedCredits(), estimatedTotalCredits);
        if (projectedMonthlyUsage > budget.monthlyCreditLimit()) {
            return JobDiscoveryBudgetStatus.DENIED_MONTHLY_LIMIT;
        }

        long remainingAfterRun = snapshot.remainingCredits() - estimatedTotalCredits;
        if (remainingAfterRun < budget.reserveCredits()) {
            return JobDiscoveryBudgetStatus.DENIED_RESERVE_LIMIT;
        }

        return JobDiscoveryBudgetStatus.ALLOWED;
    }
}
