package com.darya.jobassistant.vacancyrecommendation;

/**
 * Stable categories for {@link VacancyRecommendationIssue}. Deliberately covers only genuine
 * failures - normal control-flow outcomes (below-threshold, manually-reviewed, already-notified,
 * a reused existing analysis) are never represented as an issue; they are counters on {@link
 * VacancyRecommendationProcessingResult} instead, matching {@code JobDiscoveryIssueCategory}'s
 * convention.
 */
public enum VacancyRecommendationIssueCategory {
    ANALYSIS_FAILED,
    NOTIFICATION_FAILED,
    LEASE_LOST,
    TASK_DEAD
}
