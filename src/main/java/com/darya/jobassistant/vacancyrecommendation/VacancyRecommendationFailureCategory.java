package com.darya.jobassistant.vacancyrecommendation;

/**
 * Stable, sanitized failure categories for {@code VacancyRecommendationTask.lastErrorCategory} -
 * never a raw OpenAI/Telegram response body, exception message, or stack trace. Determines both
 * observability (what actually went wrong, in aggregate, across runs) and recoverability (see
 * {@code VacancyRecommendationProcessingService#isRecoverable}).
 */
public enum VacancyRecommendationFailureCategory {

    /** The AI provider call did not complete within its configured timeout. */
    ANALYSIS_TIMEOUT,

    /** The AI provider rejected the call due to rate limiting. */
    ANALYSIS_RATE_LIMIT,

    /** The AI provider call failed for any other reason (connection, server error, SDK failure). */
    ANALYSIS_PROVIDER_ERROR,

    /** The AI provider returned a response that failed validation (e.g. no analysis produced). */
    ANALYSIS_INVALID_RESPONSE,

    /** Telegram rejected the send in a way retrying later may resolve (see {@code JobNotificationFailureType#TEMPORARY_FAILURE}). */
    TELEGRAM_TRANSIENT_ERROR,

    /** Telegram rejected the send in a way retrying is unlikely to resolve (see {@code JobNotificationFailureType#PERMANENT_FAILURE}). */
    TELEGRAM_PERMANENT_ERROR,

    /** A database state transition did not apply as expected (e.g. a lost lease, a lost unique race). */
    DATABASE_CONFLICT,

    /** Any other unexpected failure. */
    INTERNAL_ERROR
}
