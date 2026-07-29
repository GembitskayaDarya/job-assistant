package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Firecrawl {@code POST /v2/scrape} response shape. Only {@code success} and {@code data.markdown}
 * are declared - every other field Firecrawl returns (metadata, warning, creditsUsed, etc.) is
 * tolerated via {@code ignoreUnknown} rather than modeled, since it must never reach the
 * provider-neutral {@link com.darya.jobassistant.integrations.jobsearch.JobPageContent}.
 * Package-private: never crosses the {@code JobPageFetchPort} boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FirecrawlScrapeResponseDto(Boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String markdown) {
    }
}
