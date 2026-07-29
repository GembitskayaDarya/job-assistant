package com.darya.jobassistant.jobdiscovery.budget;

/**
 * Stable, provider-neutral outcome of one pre-run budget assessment - see {@link
 * JobDiscoveryBudgetPolicy} for how {@link #ALLOWED}/{@link #DENIED_PER_RUN_LIMIT}/{@link
 * #DENIED_MONTHLY_LIMIT}/{@link #DENIED_RESERVE_LIMIT} are decided, and {@code
 * JobDiscoveryService} for {@link #NOT_REQUIRED} (zero executable queries planned) and {@link
 * #UNAVAILABLE} (the provider's credit balance could not be verified). Every non-{@link #ALLOWED}
 * status must result in zero Search/Scrape/Extraction/persistence calls for that run.
 */
public enum JobDiscoveryBudgetStatus {
    ALLOWED,
    DENIED_PER_RUN_LIMIT,
    DENIED_MONTHLY_LIMIT,
    DENIED_RESERVE_LIMIT,
    UNAVAILABLE,
    NOT_REQUIRED
}
