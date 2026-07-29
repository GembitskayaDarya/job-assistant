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
 */
@ConfigurationProperties(prefix = "job-discovery")
public record JobDiscoveryProperties(boolean enabled, Execution execution) {

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
        }
    }

    private static void requireInRange(int value, int min, int max, String propertyName) {
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
}
