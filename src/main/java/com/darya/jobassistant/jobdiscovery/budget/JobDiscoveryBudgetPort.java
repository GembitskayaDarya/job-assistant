package com.darya.jobassistant.jobdiscovery.budget;

/**
 * Port between {@code JobDiscoveryService} orchestration and an external provider account API,
 * verifying that a planned discovery run stays within its configured credit budget before any
 * Search/Scrape/Extraction call is made. A port is justified here (unlike the rest of this
 * project's package-by-feature modules - see {@code CLAUDE.md}) because this is exactly the
 * integrations boundary: today's only implementation, {@code FirecrawlJobDiscoveryBudgetAdapter},
 * talks to Firecrawl's account API, and a future second credit-based provider would need its own.
 *
 * <p>Implementations must never throw: every failure mode - HTTP error, timeout, malformed
 * response, connection failure, or an internal computation error - must be converted into a {@link
 * JobDiscoveryBudgetStatus#UNAVAILABLE} {@link JobDiscoveryBudgetDecision} instead, so a caller
 * never needs exception handling to stay fail-closed. Implementations must make at most one
 * network request per call - no retry, repeat, or fallback call.
 */
public interface JobDiscoveryBudgetPort {

    JobDiscoveryBudgetDecision assessBudget(JobDiscoveryBudgetRequest request);
}
