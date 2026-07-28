package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.List;
import java.util.UUID;

/**
 * The complete, immutable result of one {@link VacancyCanonicalUrlLegacyPlanner#plan} run: every
 * legacy row classified, grouped by outcome. Package-private - both {@code
 * VacancyCanonicalUrlAuditService} (read-only reporting) and {@code
 * VacancyCanonicalUrlBackfillService} (DRY_RUN/APPLY) consume this directly rather than each
 * re-deriving classification from raw rows, so there is exactly one place the classification rules
 * live.
 */
record VacancyCanonicalUrlLegacyPlan(
        int totalLegacyRows,
        int scannedBatchCount,
        List<SafeCanonicalUrlAssignment> safeAssignments,
        List<UUID> invalidSourceUrlVacancyIds,
        List<LegacyToLegacyCollisionGroup> legacyToLegacyCollisionGroups,
        List<LegacyToCurrentCollision> legacyToCurrentCollisions) {

    VacancyCanonicalUrlLegacyPlan {
        safeAssignments = List.copyOf(safeAssignments);
        invalidSourceUrlVacancyIds = List.copyOf(invalidSourceUrlVacancyIds);
        legacyToLegacyCollisionGroups = List.copyOf(legacyToLegacyCollisionGroups);
        legacyToCurrentCollisions = List.copyOf(legacyToCurrentCollisions);
    }

    int legacyToLegacyCollisionRows() {
        return legacyToLegacyCollisionGroups.stream().mapToInt(group -> group.vacancyIds().size()).sum();
    }

    /** True if any row is not (yet) safe to backfill - an invalid URL or either collision type. */
    boolean hasBlockers() {
        return !invalidSourceUrlVacancyIds.isEmpty()
                || !legacyToLegacyCollisionGroups.isEmpty()
                || !legacyToCurrentCollisions.isEmpty();
    }
}
