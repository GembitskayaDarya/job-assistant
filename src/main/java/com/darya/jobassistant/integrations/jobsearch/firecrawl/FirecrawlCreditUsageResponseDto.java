package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Firecrawl {@code GET /v2/team/credit-usage} response shape. Only the four documented {@code
 * data} fields are declared; anything else Firecrawl returns is tolerated via {@code
 * ignoreUnknown} rather than modeled, since it must never reach the provider-neutral {@link
 * com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryCreditSnapshot}.
 *
 * <p>Billing period timestamps are kept as raw strings and parsed to {@link java.time.Instant} by
 * {@link FirecrawlJobDiscoveryBudgetAdapter} - deliberately not bound as {@code Instant} fields
 * here, so response mapping never depends on whether the injected {@code ObjectMapper} has a
 * java-time module registered.
 *
 * <p>Package-private: never crosses the {@code JobDiscoveryBudgetPort} boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FirecrawlCreditUsageResponseDto(Boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Long remainingCredits, Long planCredits, String billingPeriodStart, String billingPeriodEnd) {
    }
}
