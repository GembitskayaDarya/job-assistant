package com.darya.jobassistant.jobdiscovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activation and per-run cost bounds for {@code JobDiscoveryService}. Deliberately provider-
 * neutral: nothing here names Firecrawl or OpenAI - {@code JobDiscoveryService} itself is only
 * wired up when {@code enabled=true} <em>and</em> a real {@code JobSearchPort}/{@code
 * JobPageFetchPort} bean exists (today, only when {@code firecrawl.enabled=true}); if discovery is
 * enabled without those beans present, Spring fails application startup with its own clear
 * "no qualifying bean" message rather than this class silently letting a non-functional service be
 * created.
 *
 * <p>Validation only runs when {@code enabled=true}, matching {@code FirecrawlProperties}/{@code
 * JobMonitoringProperties}'s convention: the application must keep starting with defaulted (even
 * out-of-range, if ever overridden) execution bounds as long as discovery stays off.
 *
 * <p>{@link #budget()} is deliberately provider-neutral: it names no Firecrawl concept and holds
 * only spend-policy limits (monthly cap, reserve, per-run cap) - see {@code
 * FirecrawlProperties.Cost} for the separate, provider-specific pricing assumptions used to turn a
 * planned run into a credit estimate, and {@code JobDiscoveryBudgetPolicy} for how these limits are
 * applied to that estimate.
 */
@ConfigurationProperties(prefix = "job-discovery")
public record JobDiscoveryProperties(boolean enabled, Execution execution, Budget budget) {

    private static final int MIN_MAX_QUERIES_PER_RUN = 1;
    private static final int MAX_MAX_QUERIES_PER_RUN = 10;
    private static final int MIN_MAX_SCRAPES_PER_RUN = 1;
    private static final int MAX_MAX_SCRAPES_PER_RUN = 50;
    private static final int MIN_MAX_EXTRACTIONS_PER_RUN = 1;
    private static final int MAX_MAX_EXTRACTIONS_PER_RUN = 50;
    private static final int MIN_MAX_UNIQUE_REFERENCES_PER_RUN = 1;
    private static final int MAX_MAX_UNIQUE_REFERENCES_PER_RUN = 500;
    private static final int MIN_MAX_REPORTED_ISSUES = 0;
    private static final int MAX_MAX_REPORTED_ISSUES = 1000;
    private static final long MIN_MONTHLY_CREDIT_LIMIT = 1;
    private static final long MAX_MONTHLY_CREDIT_LIMIT = 1_000_000;
    private static final long MIN_RESERVE_CREDITS = 0;
    private static final long MAX_RESERVE_CREDITS = 1_000_000;
    private static final long MIN_MAX_ESTIMATED_CREDITS_PER_RUN = 1;
    private static final long MAX_MAX_ESTIMATED_CREDITS_PER_RUN = 100_000;

    public JobDiscoveryProperties {
        if (enabled) {
            if (execution == null) {
                throw new IllegalArgumentException("job-discovery.execution must be set when job-discovery.enabled=true");
            }
            requireInRange(execution.maxQueriesPerRun(), MIN_MAX_QUERIES_PER_RUN, MAX_MAX_QUERIES_PER_RUN,
                    "job-discovery.execution.max-queries-per-run");
            requireInRange(execution.maxScrapesPerRun(), MIN_MAX_SCRAPES_PER_RUN, MAX_MAX_SCRAPES_PER_RUN,
                    "job-discovery.execution.max-scrapes-per-run");
            requireInRange(execution.maxExtractionsPerRun(), MIN_MAX_EXTRACTIONS_PER_RUN, MAX_MAX_EXTRACTIONS_PER_RUN,
                    "job-discovery.execution.max-extractions-per-run");
            requireInRange(execution.maxUniqueReferencesPerRun(), MIN_MAX_UNIQUE_REFERENCES_PER_RUN, MAX_MAX_UNIQUE_REFERENCES_PER_RUN,
                    "job-discovery.execution.max-unique-references-per-run");
            requireInRange(execution.maxReportedIssues(), MIN_MAX_REPORTED_ISSUES, MAX_MAX_REPORTED_ISSUES,
                    "job-discovery.execution.max-reported-issues");
            if (execution.maxExtractionsPerRun() > execution.maxScrapesPerRun()) {
                throw new IllegalArgumentException(
                        "job-discovery.execution.max-extractions-per-run (" + execution.maxExtractionsPerRun()
                                + ") must not exceed job-discovery.execution.max-scrapes-per-run (" + execution.maxScrapesPerRun() + ")");
            }
            if (budget == null) {
                throw new IllegalArgumentException("job-discovery.budget must be set when job-discovery.enabled=true");
            }
            requireInRange(budget.monthlyCreditLimit(), MIN_MONTHLY_CREDIT_LIMIT, MAX_MONTHLY_CREDIT_LIMIT,
                    "job-discovery.budget.monthly-credit-limit");
            requireInRange(budget.reserveCredits(), MIN_RESERVE_CREDITS, MAX_RESERVE_CREDITS,
                    "job-discovery.budget.reserve-credits");
            requireInRange(budget.maxEstimatedCreditsPerRun(), MIN_MAX_ESTIMATED_CREDITS_PER_RUN, MAX_MAX_ESTIMATED_CREDITS_PER_RUN,
                    "job-discovery.budget.max-estimated-credits-per-run");
            if (budget.maxEstimatedCreditsPerRun() > budget.monthlyCreditLimit()) {
                throw new IllegalArgumentException(
                        "job-discovery.budget.max-estimated-credits-per-run (" + budget.maxEstimatedCreditsPerRun()
                                + ") must not exceed job-discovery.budget.monthly-credit-limit (" + budget.monthlyCreditLimit() + ")");
            }
        }
    }

    private static void requireInRange(int value, int min, int max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max + ", but was " + value);
        }
    }

    private static void requireInRange(long value, long min, long max, String propertyName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + min + " and " + max + ", but was " + value);
        }
    }

    /** Per-run hard cost bounds - see {@code JobDiscoveryService} for how each one is enforced. */
    public record Execution(
            int maxQueriesPerRun,
            int maxScrapesPerRun,
            int maxExtractionsPerRun,
            int maxUniqueReferencesPerRun,
            int maxReportedIssues
    ) {
    }

    /**
     * Provider-neutral spend-policy limits enforced by {@code JobDiscoveryBudgetPolicy} against a
     * verified Firecrawl credit snapshot before every non-empty discovery run.
     */
    public record Budget(
            long monthlyCreditLimit,
            long reserveCredits,
            long maxEstimatedCreditsPerRun
    ) {
    }
}
