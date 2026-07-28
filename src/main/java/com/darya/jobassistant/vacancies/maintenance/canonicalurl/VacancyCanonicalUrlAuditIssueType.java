package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

/**
 * Why a legacy {@code Vacancy} (one with {@code canonical_url IS NULL}) is not - yet -
 * {@code SAFE_TO_BACKFILL}. There is deliberately no {@code SAFE_TO_BACKFILL} value here: a safe
 * row has nothing further to report beyond being counted (see {@code
 * VacancyCanonicalUrlAuditReport#safeToBackfillRows}), so it never produces a {@link
 * VacancyCanonicalUrlAuditIssue}.
 */
public enum VacancyCanonicalUrlAuditIssueType {

    /**
     * {@code sourceUrl} could not be canonicalized at all - blank, malformed, relative, hostless,
     * a non-http(s) scheme, contains user-info, or otherwise rejected by {@code
     * VacancyUrlCanonicalizer}.
     */
    INVALID_SOURCE_URL,

    /**
     * This row's canonical candidate is shared by at least one other legacy row. See {@link
     * VacancyCanonicalUrlAuditIssue#relatedVacancyIds()} for the rest of the group.
     */
    LEGACY_TO_LEGACY_COLLISION,

    /**
     * This row's canonical candidate is already used by a different {@code Vacancy} whose {@code
     * canonical_url} is already populated (non-null).
     */
    LEGACY_TO_CURRENT_COLLISION
}
