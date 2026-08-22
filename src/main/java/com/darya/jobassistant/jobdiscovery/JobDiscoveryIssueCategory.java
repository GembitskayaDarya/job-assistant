package com.darya.jobassistant.jobdiscovery;

/**
 * Stable categories for {@link JobDiscoveryIssue}. Deliberately covers only genuine failures and
 * explicit, machine-readable candidate rejections - normal control-flow outcomes (a duplicate
 * within the run, an already-persisted vacancy, a canonical-conflict race resolved as
 * already-existing, or a configured limit being reached) are never represented as an issue; they
 * are counters/flags on {@link JobDiscoveryRunResult} instead.
 */
public enum JobDiscoveryIssueCategory {
    SEARCH_FAILED,
    INVALID_REFERENCE_URL,
    SCRAPE_FAILED,
    EXTRACTION_FAILED,
    PERSISTENCE_FAILED,

    /** URL classified {@code SEARCH_OR_LISTING_PAGE} and expansion produced zero direct candidates. */
    SEARCH_OR_LISTING_URL,

    /** URL classified {@code UNSUPPORTED_OR_INVALID} by {@code JobUrlClassifier}. */
    UNSUPPORTED_URL,

    /** Rejected by {@code JobGeographyPolicy} (early hint-based or final extracted-location check). */
    OUTSIDE_TARGET_GEO,

    /** Rejected by the backend-role signal check (early title-based or final extracted-text check). */
    NON_BACKEND_ROLE,

    /** Scraped content too short to plausibly contain a real vacancy - AI extraction was never attempted. */
    INSUFFICIENT_DATA,

    /**
     * The canonical-URL duplicate pre-check ({@code VacancyRepository#existsByCanonicalUrl})
     * failed with a repository/database {@code DataAccessException} - an infrastructure failure,
     * not a domain rejection. Existence is unknown, so the candidate is skipped rather than
     * assumed novel; see {@code JobDiscoveryRunResult#persistencePrecheckFailures}.
     */
    PERSISTENCE_PRECHECK_FAILED
}
