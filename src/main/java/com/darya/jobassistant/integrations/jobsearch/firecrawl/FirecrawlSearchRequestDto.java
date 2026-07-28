package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import java.util.List;

/**
 * Firecrawl {@code POST /v2/search} request body. Deliberately omits {@code scrapeOptions} - this
 * adapter only ever requests lightweight search references, never scraped page content.
 */
public record FirecrawlSearchRequestDto(
        String query,
        int limit,
        List<String> sources,
        boolean ignoreInvalidURLs
) {
}
