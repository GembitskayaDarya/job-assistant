package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

/**
 * The immutable result of one {@link VacancyCanonicalUrlBackfillService#dryRun()} or {@link
 * VacancyCanonicalUrlBackfillService#apply()} run.
 *
 * <p>For {@link VacancyCanonicalUrlBackfillMode#DRY_RUN}, {@code updatedRows} is always {@code 0}
 * and {@code committed} is always {@code false} - nothing was written. For a successful {@code
 * APPLY}, {@code updatedRows} always equals {@code plannedAssignments} and {@code committed} is
 * {@code true}. An {@code APPLY} that hits a blocker or an invariant violation never produces a
 * result at all - it throws {@link VacancyCanonicalUrlBackfillBlockedException} or {@link
 * VacancyCanonicalUrlBackfillInvariantViolationException} instead, so there is no "unsuccessful
 * APPLY" result value to check for; a caller either gets a result with {@code committed=true}, or
 * an exception.
 *
 * @param legacyRowsScanned every {@code canonical_url IS NULL} row seen during this run's scan
 * @param plannedAssignments legacy rows the fresh plan classified as safe to backfill
 * @param updatedRows rows actually written by this run
 * @param invalidRows legacy rows whose {@code sourceUrl} could not be canonicalized
 * @param legacyCollisionGroups distinct canonical values shared by two or more legacy rows
 * @param legacyCollisionRows total rows across all {@code legacyCollisionGroups}
 * @param currentCollisionRows legacy rows whose candidate already belongs to a populated row
 * @param scannedBatchCount number of non-empty pages fetched while scanning legacy rows
 */
public record VacancyCanonicalUrlBackfillResult(
        VacancyCanonicalUrlBackfillMode mode,
        int legacyRowsScanned,
        int plannedAssignments,
        int updatedRows,
        int invalidRows,
        int legacyCollisionGroups,
        int legacyCollisionRows,
        int currentCollisionRows,
        boolean committed,
        int scannedBatchCount) {
}
