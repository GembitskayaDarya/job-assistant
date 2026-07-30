package com.darya.jobassistant.vacancyrecommendation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deterministic, immutable outcome of one {@code VacancyRecommendationProcessingService#processPending()}
 * run. Never carries a JPA entity, Telegram payload, or AI content - only bounded counters and
 * sanitized {@link VacancyRecommendationIssue}s, matching {@code JobDiscoveryRunResult}'s
 * convention.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@link #claimedTasks} is the number of tasks this run claimed (including {@link
 *       #leaseRecoveredTasks}, a subset of it recovered from another worker's expired lease);
 *   <li>{@link #completedTasks} = {@link #notifiedTasks} + {@link #belowThresholdTasks} + manually
 *       -reviewed + already-notified + non-automatic-analysis outcomes - every task that reached
 *       {@code COMPLETED} this run, regardless of which specific outcome;
 *   <li>{@link #analysisAttempts} counts new AI calls made this run; {@link #analysisReused}
 *       counts tasks that reused an existing {@code AUTOMATIC_DISCOVERY} analysis without calling
 *       AI again;
 *   <li>{@link #retryScheduled} + {@link #deadTasks} + {@link #completedTasks} accounts for every
 *       claimed task this run (a task is always left in exactly one of {@code RETRY_WAIT}, {@code
 *       DEAD}, or {@code COMPLETED} by the time processing of it finishes).
 * </ul>
 */
public record VacancyRecommendationProcessingResult(
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        int claimedTasks,
        int completedTasks,
        int notifiedTasks,
        int belowThresholdTasks,
        int manuallyReviewedTasks,
        int alreadyNotifiedTasks,
        int analysisAttempts,
        int analysisReused,
        int analysisFailures,
        int notificationAttempts,
        int notificationFailures,
        int retryScheduled,
        int deadTasks,
        int leaseRecoveredTasks,
        List<VacancyRecommendationIssue> issues,
        int omittedIssueCount
) {
    public VacancyRecommendationProcessingResult {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
