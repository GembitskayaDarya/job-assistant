package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the read-only {@link VacancyCanonicalUrlAuditService} / {@code
 * VacancyCanonicalUrlAuditRunner}. Validation only runs when {@code enabled=true}, matching
 * {@code FirecrawlProperties}/{@code JobMonitoringProperties}/{@code VacancyImportCleanupProperties}'s
 * convention: the application must keep starting with no audit configuration at all as long as the
 * audit stays disabled, which it is by default - this is a maintenance capability, never required
 * for normal operation.
 */
@ConfigurationProperties(prefix = "vacancy-canonical-url-audit")
public record VacancyCanonicalUrlAuditProperties(boolean enabled, int batchSize, int maxReportedIssues) {

    /** Generous enough for a single maintenance run's page size, small enough to bound one query's result set. */
    private static final int MAX_BATCH_SIZE = 5000;

    /** Bounds in-memory issue detail and the runner's log volume for one run. */
    private static final int MAX_REPORTED_ISSUES_UPPER_BOUND = 10_000;

    public VacancyCanonicalUrlAuditProperties {
        if (enabled) {
            if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "vacancy-canonical-url-audit.batch-size must be between 1 and " + MAX_BATCH_SIZE
                                + " when vacancy-canonical-url-audit.enabled=true, but was " + batchSize);
            }
            if (maxReportedIssues < 0 || maxReportedIssues > MAX_REPORTED_ISSUES_UPPER_BOUND) {
                throw new IllegalArgumentException(
                        "vacancy-canonical-url-audit.max-reported-issues must be between 0 and "
                                + MAX_REPORTED_ISSUES_UPPER_BOUND + " when vacancy-canonical-url-audit.enabled=true, but was "
                                + maxReportedIssues);
            }
        }
    }
}
