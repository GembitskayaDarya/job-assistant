package com.darya.jobassistant.vacancyrecommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single, provider-neutral score threshold that decides whether an AI-matched vacancy is
 * "good enough" to notify a recipient about - shared by the legacy scheduled monitoring workflow
 * ({@code JobMonitoringScheduler}) and automatic web-discovery recommendation processing ({@code
 * VacancyRecommendationProcessingService}). Deliberately its own tiny {@code
 * @ConfigurationProperties} record, not a field on either workflow's own properties (previously
 * {@code JobMonitoringProperties.minimumScore}): the same business decision - "is this score worth
 * notifying about" - must never be configured twice with values that can silently drift apart.
 *
 * <p>Always bound and validated, independent of whether monitoring or recommendation processing is
 * actually enabled - matching this project's convention for small, always-relevant policy values
 * (e.g. {@code JpaAuditingConfig}), since an out-of-range value here is a configuration mistake
 * worth catching at startup regardless of which caller would have used it first.
 */
@ConfigurationProperties(prefix = "recommendation-policy")
public record RecommendationPolicyProperties(int minimumScore) {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public RecommendationPolicyProperties {
        if (minimumScore < MIN_SCORE || minimumScore > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "recommendation-policy.minimum-score must be between %d and %d, but was %d"
                            .formatted(MIN_SCORE, MAX_SCORE, minimumScore));
        }
    }
}
