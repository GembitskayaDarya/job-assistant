package com.darya.jobassistant.jobdiscovery.budget;

import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import java.util.List;

/**
 * Everything a {@link JobDiscoveryBudgetPort} implementation needs to estimate one run's maximum
 * provider spend - never the full {@code CandidateProfile} or any other orchestration state.
 * {@link #searchRequests()} is the exact, already-bounded list of {@link JobSearchRequest}s {@code
 * JobDiscoveryService} may actually execute this run (planned queries already truncated to {@code
 * job-discovery.execution.max-queries-per-run}), not every query the planner produced.
 */
public record JobDiscoveryBudgetRequest(List<JobSearchRequest> searchRequests, int maxScrapesPerRun) {

    public JobDiscoveryBudgetRequest {
        if (searchRequests == null || searchRequests.isEmpty()) {
            throw new IllegalArgumentException("searchRequests must not be null or empty");
        }
        if (maxScrapesPerRun <= 0) {
            throw new IllegalArgumentException("maxScrapesPerRun must be positive");
        }
        searchRequests = List.copyOf(searchRequests);
    }
}
