package com.darya.jobassistant.vacancyrecommendation;

/**
 * Stable lifecycle states of one {@code VacancyRecommendationTask} row. Persisted verbatim as
 * {@code .name()} (see {@code VacancyRecommendationTaskEntity}), never a JPA enum ordinal, so the
 * database's own {@code chk_vacancy_recommendation_task_status} CHECK constraint (V15) stays
 * meaningful independent of this enum's declaration order.
 */
public enum VacancyRecommendationTaskStatus {

    /** Newly registered, never yet claimed. */
    PENDING,

    /** Currently claimed and being worked by exactly one processing attempt. */
    PROCESSING,

    /** A recoverable failure was handled; eligible for reclaiming once {@code nextAttemptAt} passes. */
    RETRY_WAIT,

    /** Terminal success - see {@code VacancyRecommendationTaskOutcome} for which one. */
    COMPLETED,

    /** Terminal failure - {@code maxAttempts} exhausted or a non-recoverable failure. */
    DEAD
}
