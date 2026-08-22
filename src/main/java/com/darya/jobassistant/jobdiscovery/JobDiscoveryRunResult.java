package com.darya.jobassistant.jobdiscovery;

import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
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
 *   <li>Every raw {@code DiscoveredJobReference} from a successful search, plus every candidate
 *       link extracted from a fetched listing page, is classified exactly once by {@code
 *       JobUrlClassifier} into {@link
 *       #directUrlCandidates}, {@link #listingUrlCandidates}, or {@link #unsupportedUrlCandidates}
 *       (after first surviving canonicalization - a canonicalization failure is counted as {@link
 *       #invalidReferences} instead and never reaches classification). {@link #discoveredReferences}
 *       counts only the top-level, pre-classification total returned directly by search - it is not
 *       the sum of the three classification counters, since links discovered by expanding a listing
 *       page are also classified but were never part of {@link #discoveredReferences}.
 *   <li>{@link #uniqueReferencesAccepted} counts every valid, non-duplicate, under-the-cap
 *       canonical candidate accepted into the run's candidate set - direct candidates and listing-
 *       expanded direct candidates alike. It is a superset of {@link #existingVacanciesSkipped},
 *       {@link #geoRejected}, {@link #nonBackendRoleRejected}, {@link #scrapeAttempts}-derived
 *       outcomes, and candidates never reached because a paid budget was already exhausted.
 *   <li>{@link #listingPagesFetched} (successes + failures) always counts against the same shared
 *       {@link #scrapeAttempts}/{@link #scrapeSuccesses}/{@link #scrapeFailures} totals as any
 *       direct candidate's scrape - there is one Firecrawl-scrape budget, not two.
 *   <li>{@link #scrapeSuccesses} is always followed by exactly one of: an {@link
 *       #insufficientDataRejected} outcome, or one extraction attempt (for a direct candidate's own
 *       scrape - a listing page's scrape is never followed by an extraction attempt at all, since a
 *       listing page is never extracted as a vacancy). {@link #extractionSuccesses} is always
 *       followed by exactly one persistence attempt.
 *   <li>{@link #createdVacancies} + {@link #alreadyExistingAfterRace} + {@link
 *       #persistenceFailures} == {@link #persistenceAttempts}.
 *   <li>{@link #persistencePrecheckFailures} counts candidates whose canonical-URL duplicate
 *       pre-check ({@code VacancyRepository#existsByCanonicalUrl}) failed with a repository/
 *       database {@code DataAccessException} - existence was never determined, so the candidate is
 *       skipped before any scrape/extraction/persistence work. It is deliberately disjoint from,
 *       and must never be summed into, {@link #existingVacanciesSkipped} (a successful pre-check
 *       that found an existing row), {@link #persistenceAttempts}/{@link #persistenceFailures}
 *       (which only count the later {@code VacancyIngestionService#persistDiscovered} write - a
 *       pre-check failure means that write is never attempted), or {@link #uniqueReferencesAccepted}
 *       -derived accounting for any other stage.
 *   <li>{@link #geoRejected} and {@link #nonBackendRoleRejected} each count both the cheap,
 *       hint-based rejection (before scrape) and the final, extracted-data rejection (before
 *       persistence) for the same reason - they are not split by stage, since both stages apply the
 *       exact same policy to different text and either one is a terminal, observable rejection.
 *   <li>{@link #budgetDecision} explains why a run stopped before any Search: when its status is
 *       not {@code ALLOWED}, every counter above is zero and {@link #createdVacancyIds}/{@link
 *       #issues} are empty - a budget denial is never reported as a {@link JobDiscoveryIssue}.
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
        int persistencePrecheckFailures,
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
        int directUrlCandidates,
        int listingUrlCandidates,
        int unsupportedUrlCandidates,
        int listingPagesFetched,
        int listingPagesFetchFailed,
        int listingPagesWithNoCandidates,
        int listingCandidateLinksExtracted,
        int nestedListingLinksDiscarded,
        boolean listingPageLimitReached,
        int geoRejected,
        int nonBackendRoleRejected,
        int insufficientDataRejected,
        List<UUID> createdVacancyIds,
        List<JobDiscoveryIssue> issues,
        int omittedIssueCount,
        JobDiscoveryBudgetDecision budgetDecision
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
        if (budgetDecision == null) {
            throw new IllegalArgumentException("budgetDecision must not be null");
        }
        createdVacancyIds = createdVacancyIds == null ? List.of() : List.copyOf(createdVacancyIds);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
