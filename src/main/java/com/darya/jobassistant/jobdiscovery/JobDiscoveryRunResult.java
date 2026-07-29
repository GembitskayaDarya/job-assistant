package com.darya.jobassistant.jobdiscovery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic, immutable outcome of one {@code JobDiscoveryService} run. Never carries a JPA
 * {@code Vacancy} entity - {@link #createdVacancyIds} exposes only the UUIDs of vacancies actually
 * {@code CREATED} during this run (never one resolved as {@code ALREADY_EXISTS}, whether by the
 * pre-check or by a canonical-conflict race).
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@link #executedQueries} counts every search attempted (success or failure);
 *       {@link #failedQueries} is the subset that failed.
 *   <li>{@link #uniqueReferencesAccepted} counts every valid, non-duplicate, under-the-cap
 *       canonical reference accepted into the run's candidate set - it is a superset of {@link
 *       #existingVacanciesSkipped}, {@link #scrapeAttempts}-derived outcomes, and candidates never
 *       reached because a paid budget was already exhausted.
 *   <li>{@link #scrapeSuccesses} is always followed by exactly one extraction attempt; {@link
 *       #extractionSuccesses} is always followed by exactly one persistence attempt.
 *   <li>{@link #createdVacancies} + {@link #alreadyExistingAfterRace} + {@link
 *       #persistenceFailures} == {@link #persistenceAttempts}.
 * </ul>
 */
public record JobDiscoveryRunResult(
        Instant startedAt,
        Instant completedAt,
        Duration duration,
        int plannedQueries,
        int executedQueries,
        int failedQueries,
        int discoveredReferences,
        int invalidReferences,
        int duplicateReferencesInRun,
        int existingVacanciesSkipped,
        int uniqueReferencesAccepted,
        int scrapeAttempts,
        int scrapeSuccesses,
        int scrapeFailures,
        int extractionAttempts,
        int extractionSuccesses,
        int extractionFailures,
        int persistenceAttempts,
        int createdVacancies,
        int alreadyExistingAfterRace,
        int persistenceFailures,
        boolean queryLimitReached,
        boolean scrapeLimitReached,
        boolean extractionLimitReached,
        boolean uniqueReferenceLimitReached,
        List<UUID> createdVacancyIds,
        List<JobDiscoveryIssue> issues,
        int omittedIssueCount
) {
    public JobDiscoveryRunResult {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        createdVacancyIds = createdVacancyIds == null ? List.of() : List.copyOf(createdVacancyIds);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
