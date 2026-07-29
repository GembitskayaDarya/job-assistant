package com.darya.jobassistant.jobdiscovery;

/**
 * Thrown only for the small set of unrecoverable orchestration invariants that must fail the
 * whole {@code JobDiscoveryService} run rather than being isolated per-item: today, only "the
 * candidate profile could not be loaded" (nothing downstream - planning, search, everything else -
 * can proceed without one). Query-planning failure ({@code JobSearchPlanningException}) is left to
 * propagate unwrapped for the same reason. Per-query, per-reference, per-scrape, per-extraction,
 * and per-persistence failures are never represented this way - see {@link JobDiscoveryRunResult}
 * and {@link JobDiscoveryIssue} for how those are isolated and reported instead.
 */
public class JobDiscoveryOrchestrationException extends RuntimeException {

    public JobDiscoveryOrchestrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
