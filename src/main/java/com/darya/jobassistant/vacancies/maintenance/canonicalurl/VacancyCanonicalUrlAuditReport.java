package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.List;

/**
 * The immutable result of one {@link VacancyCanonicalUrlAuditService#audit()} run. Every count
 * field reflects the *complete* scan regardless of {@code vacancy-canonical-url-audit.max-reported-issues}
 * - only {@link #issues()} itself is bounded by that setting; {@link #omittedIssueCount()} is how
 * many additional issues exist beyond what {@link #issues()} carries, so a caller can always tell
 * counts and detail apart.
 *
 * @param totalLegacyRows number of {@code Vacancy} rows scanned with {@code canonical_url IS NULL}
 * @param safeToBackfillRows rows whose canonical candidate is unique among legacy rows and unused
 *     by any currently-populated {@code canonical_url}
 * @param invalidSourceUrlRows rows whose {@code sourceUrl} could not be canonicalized at all
 * @param legacyToLegacyCollisionGroups distinct canonical values shared by two or more legacy rows
 * @param legacyToLegacyCollisionRows total rows across all {@link #legacyToLegacyCollisionGroups}
 * @param legacyToCurrentCollisionRows rows whose canonical candidate already belongs to a
 *     different, already-populated {@code Vacancy}
 * @param scannedBatchCount number of non-empty pages fetched while scanning legacy rows
 * @param omittedIssueCount issues that exist but were not added to {@link #issues()} because
 *     {@code max-reported-issues} was reached first
 * @param issues bounded, most-severe-first-by-discovery-order sample of non-safe rows; see {@link
 *     VacancyCanonicalUrlAuditIssue}
 */
public record VacancyCanonicalUrlAuditReport(
        int totalLegacyRows,
        int safeToBackfillRows,
        int invalidSourceUrlRows,
        int legacyToLegacyCollisionGroups,
        int legacyToLegacyCollisionRows,
        int legacyToCurrentCollisionRows,
        int scannedBatchCount,
        int omittedIssueCount,
        List<VacancyCanonicalUrlAuditIssue> issues) {

    public VacancyCanonicalUrlAuditReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean issuesTruncated() {
        return omittedIssueCount > 0;
    }
}
