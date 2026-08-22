package com.darya.jobassistant.jobdiscovery.url;

/**
 * The explicit URL-lifecycle classification every discovery candidate must pass through before it
 * may be scraped, extracted, persisted as a {@code Vacancy}, or analyzed. See {@link
 * JobUrlClassifier} for how a {@code CanonicalVacancyUrl} is assigned one of these.
 */
public enum JobUrlType {

    /** A concrete vacancy page - the only type allowed to reach extraction/persistence/analysis. */
    DIRECT_JOB,

    /**
     * A search-result, category, "/jobs" collection, or other multi-listing page. Never persisted
     * as a {@code Vacancy} - only ever a source of further {@link #DIRECT_JOB} candidate links via
     * listing expansion (see {@code ListingCandidateLinkExtractor}).
     */
    SEARCH_OR_LISTING_PAGE,

    /** Malformed, irrelevant, or otherwise unusable - rejected without affecting the rest of the run. */
    UNSUPPORTED_OR_INVALID
}
