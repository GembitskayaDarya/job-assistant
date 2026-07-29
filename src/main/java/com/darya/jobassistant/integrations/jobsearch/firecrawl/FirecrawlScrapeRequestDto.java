package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import java.util.List;

/**
 * Firecrawl {@code POST /v2/scrape} request body. Requests Markdown only, with {@code proxy}
 * pinned to {@code "basic"} (never Firecrawl's default, {@code "auto"}, or {@code "enhanced"}) and
 * no {@code actions}/{@code screenshot} options - this adapter fetches exactly one page's clean
 * Markdown, nothing else. Package-private: never crosses the {@code JobPageFetchPort} boundary.
 */
record FirecrawlScrapeRequestDto(
        String url,
        List<String> formats,
        boolean onlyMainContent,
        boolean onlyCleanContent,
        boolean removeBase64Images,
        boolean blockAds,
        String proxy,
        int timeout
) {
}
