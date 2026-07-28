package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.List;
import java.util.UUID;

/**
 * Thrown by {@link VacancyCanonicalUrlBackfillService#apply()} when the plan it recomputed
 * <em>inside</em> the write transaction still contains an invalid source URL or either collision
 * type. Thrown before any {@code UPDATE} is issued, and always allowed to propagate out of the
 * transaction callback so the whole APPLY attempt rolls back (trivially - nothing was written yet)
 * rather than being caught and reported as a partial success.
 *
 * <p>Carries only counts and vacancy id's - never a source or canonical URL - so this can be
 * logged directly by {@code VacancyCanonicalUrlBackfillRunner} without risking a token or tracking
 * parameter leaking into the log. {@link #sampleBlockingVacancyIds()} is bounded, never the
 * complete blocker list, so a large blocked backfill can never produce an unbounded exception
 * message or log line.
 */
public class VacancyCanonicalUrlBackfillBlockedException extends RuntimeException {

    private final int invalidSourceUrlCount;
    private final int legacyToLegacyCollisionRowCount;
    private final int legacyToCurrentCollisionRowCount;
    private final List<UUID> sampleBlockingVacancyIds;

    public VacancyCanonicalUrlBackfillBlockedException(
            String message,
            int invalidSourceUrlCount,
            int legacyToLegacyCollisionRowCount,
            int legacyToCurrentCollisionRowCount,
            List<UUID> sampleBlockingVacancyIds) {
        super(message);
        this.invalidSourceUrlCount = invalidSourceUrlCount;
        this.legacyToLegacyCollisionRowCount = legacyToLegacyCollisionRowCount;
        this.legacyToCurrentCollisionRowCount = legacyToCurrentCollisionRowCount;
        this.sampleBlockingVacancyIds = List.copyOf(sampleBlockingVacancyIds);
    }

    public int invalidSourceUrlCount() {
        return invalidSourceUrlCount;
    }

    public int legacyToLegacyCollisionRowCount() {
        return legacyToLegacyCollisionRowCount;
    }

    public int legacyToCurrentCollisionRowCount() {
        return legacyToCurrentCollisionRowCount;
    }

    public List<UUID> sampleBlockingVacancyIds() {
        return sampleBlockingVacancyIds;
    }
}
