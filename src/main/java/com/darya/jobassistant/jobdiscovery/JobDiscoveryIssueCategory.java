package com.darya.jobassistant.jobdiscovery;

/**
 * Stable categories for {@link JobDiscoveryIssue}. Deliberately covers only genuine failures -
 * normal control-flow outcomes (a duplicate within the run, an already-persisted vacancy, a
 * canonical-conflict race resolved as already-existing, or a configured limit being reached) are
 * never represented as an issue; they are counters/flags on {@link JobDiscoveryRunResult} instead.
 */
public enum JobDiscoveryIssueCategory {
    SEARCH_FAILED,
    INVALID_REFERENCE_URL,
    SCRAPE_FAILED,
    EXTRACTION_FAILED,
    PERSISTENCE_FAILED
}
