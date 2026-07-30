package com.darya.jobassistant.vacancyrecommendation;

/**
 * Stable terminal outcomes of a {@code VacancyRecommendationTask} - populated exactly when {@code
 * status} is {@code COMPLETED} or {@code DEAD} (see V15's {@code
 * chk_vacancy_recommendation_task_outcome_matches_status}). Persisted verbatim as {@code .name()},
 * never a JPA enum ordinal.
 */
public enum VacancyRecommendationTaskOutcome {

    /** Analysis matched the score policy and Telegram accepted the notification. */
    NOTIFIED,

    /** Analysis completed but scored below the configured minimum. */
    BELOW_SCORE_THRESHOLD,

    /** Suppressed because the vacancy's analysis is manually reviewed (origin MANUAL or manuallyReviewedAt set). */
    MANUALLY_REVIEWED,

    /** A SENT delivery already existed for this vacancy/recipient before this task attempted to notify. */
    ALREADY_NOTIFIED,

    /** An existing analysis of non-automatic-discovery origin (LEGACY or MONITORING) already owns this vacancy. */
    ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC,

    /** {@code maxAttempts} exhausted, or a non-recoverable failure - see {@code lastErrorCategory}. */
    PERMANENT_FAILURE
}
