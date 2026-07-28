package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@link VacancyCanonicalUrlBackfillMode#DRY_RUN}/{@code APPLY} maintenance
 * backfill. Validation only runs when {@code enabled=true}, matching every other {@code
 * @ConfigurationProperties} record in this project ({@code VacancyCanonicalUrlAuditProperties},
 * {@code FirecrawlProperties}, ...): the application must keep starting with no backfill
 * configuration at all as long as the backfill stays disabled, which it is by default. Carries no
 * credential or provider setting - only {@code enabled}, {@code mode}, and {@code batchSize}.
 */
@ConfigurationProperties(prefix = "vacancy-canonical-url-backfill")
public record VacancyCanonicalUrlBackfillProperties(boolean enabled, VacancyCanonicalUrlBackfillMode mode, int batchSize) {

    /** Generous enough for a single maintenance run's page size, small enough to bound one query's result set. */
    private static final int MAX_BATCH_SIZE = 5000;

    public VacancyCanonicalUrlBackfillProperties {
        if (enabled) {
            if (mode == null) {
                throw new IllegalArgumentException(
                        "vacancy-canonical-url-backfill.mode must be set when vacancy-canonical-url-backfill.enabled=true");
            }
            if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "vacancy-canonical-url-backfill.batch-size must be between 1 and " + MAX_BATCH_SIZE
                                + " when vacancy-canonical-url-backfill.enabled=true, but was " + batchSize);
            }
        }
    }
}
