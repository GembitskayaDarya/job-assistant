package com.darya.jobassistant.vacancyrecommendation;

import java.util.UUID;

/**
 * One sanitized, bounded failure record from a processing run - see {@link
 * VacancyRecommendationProcessingResult} for how the total count is capped by a configured
 * maximum. Deliberately carries only safe, low-cardinality data - never vacancy title/description,
 * AI prompt/output, or a raw provider error body/stack trace, matching {@code JobDiscoveryIssue}'s
 * convention.
 *
 * @param category stage/category this issue occurred in
 * @param taskId the recommendation task this issue relates to
 * @param vacancyId the vacancy this issue relates to
 * @param sanitizedErrorCategory the classified, sanitized failure category (see {@link
 *     VacancyRecommendationFailureCategory}), or the failing exception's simple class name when no
 *     more specific classification applies
 */
public record VacancyRecommendationIssue(
        VacancyRecommendationIssueCategory category,
        UUID taskId,
        UUID vacancyId,
        String sanitizedErrorCategory
) {
    public VacancyRecommendationIssue {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
    }
}
