package com.darya.jobassistant.jobdiscovery;

/**
 * Thrown by {@link JobSearchQueryPlanner} when no meaningful job search query can be derived from
 * a candidate profile. Distinct from {@code JobDiscoveryException} (in {@code
 * integrations.jobsearch}), which covers external search-provider failures - this is a pure
 * application-level validation failure, never wrapping an HTTP/provider cause.
 */
public class JobSearchPlanningException extends RuntimeException {

    public JobSearchPlanningException(String message) {
        super(message);
    }
}
