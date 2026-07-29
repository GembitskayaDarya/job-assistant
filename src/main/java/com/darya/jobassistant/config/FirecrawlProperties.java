package com.darya.jobassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Configuration for the Firecrawl-backed {@code JobSearchPort} ({@code FirecrawlJobSearchAdapter},
 * {@code POST /v2/search}) and {@code JobPageFetchPort} ({@code FirecrawlJobPageFetchAdapter},
 * {@code POST /v2/scrape}) adapters. Validation only runs when {@code enabled=true}, matching
 * {@code JobMonitoringProperties} and {@code VacancyImportCleanupProperties}'s convention: the
 * application must keep starting with no {@code FIRECRAWL_API_KEY} configured as long as Firecrawl
 * stays disabled.
 *
 * <p>{@code connectTimeout}/{@code readTimeout} bound the shared {@code firecrawlWebClient}'s
 * transport-level timeouts for <em>every</em> Firecrawl call (both search and scrape), whereas
 * {@code scrapeTimeout} is the per-request, server-side budget sent as {@code timeout} in the
 * {@code /v2/scrape} request body and only tells Firecrawl how long it may spend rendering a page.
 * {@code readTimeout} must stay strictly greater than {@code scrapeTimeout} so the shared client
 * never cuts a scrape request before Firecrawl's own longer timeout could have returned.
 */
@ConfigurationProperties(prefix = "firecrawl")
public record FirecrawlProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        int searchLimit,
        Duration connectTimeout,
        Duration readTimeout,
        Duration scrapeTimeout,
        int maxMarkdownChars
) {

    private static final int MAX_SEARCH_LIMIT = 100;
    private static final Duration MIN_SCRAPE_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_SCRAPE_TIMEOUT = Duration.ofSeconds(300);
    private static final int MAX_MARKDOWN_CHARS_LIMIT = 1_000_000;

    public FirecrawlProperties {
        if (enabled) {
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalArgumentException("firecrawl.api-key must be set when firecrawl.enabled=true");
            }
            if (!StringUtils.hasText(baseUrl)) {
                throw new IllegalArgumentException("firecrawl.base-url must be set when firecrawl.enabled=true");
            }
            if (searchLimit <= 0 || searchLimit > MAX_SEARCH_LIMIT) {
                throw new IllegalArgumentException(
                        "firecrawl.search-limit must be between 1 and " + MAX_SEARCH_LIMIT
                                + " when firecrawl.enabled=true, but was " + searchLimit);
            }
            if (connectTimeout == null || !connectTimeout.isPositive()) {
                throw new IllegalArgumentException(
                        "firecrawl.connect-timeout must be positive when firecrawl.enabled=true");
            }
            if (readTimeout == null || !readTimeout.isPositive()) {
                throw new IllegalArgumentException(
                        "firecrawl.read-timeout must be positive when firecrawl.enabled=true");
            }
            if (scrapeTimeout == null
                    || scrapeTimeout.compareTo(MIN_SCRAPE_TIMEOUT) < 0
                    || scrapeTimeout.compareTo(MAX_SCRAPE_TIMEOUT) > 0) {
                throw new IllegalArgumentException(
                        "firecrawl.scrape-timeout must be between " + MIN_SCRAPE_TIMEOUT + " and " + MAX_SCRAPE_TIMEOUT
                                + " when firecrawl.enabled=true, but was " + scrapeTimeout);
            }
            if (maxMarkdownChars <= 0 || maxMarkdownChars > MAX_MARKDOWN_CHARS_LIMIT) {
                throw new IllegalArgumentException(
                        "firecrawl.max-markdown-chars must be between 1 and " + MAX_MARKDOWN_CHARS_LIMIT
                                + " when firecrawl.enabled=true, but was " + maxMarkdownChars);
            }
            if (readTimeout.compareTo(scrapeTimeout) <= 0) {
                throw new IllegalArgumentException(
                        "firecrawl.read-timeout (" + readTimeout + ") must be greater than firecrawl.scrape-timeout ("
                                + scrapeTimeout + ") when firecrawl.enabled=true, so the shared WebClient does not "
                                + "time out a scrape request before Firecrawl's own server-side timeout could return");
            }
        }
    }

    @Override
    public String toString() {
        return ("FirecrawlProperties[enabled=%s, apiKey=%s, baseUrl=%s, searchLimit=%d, connectTimeout=%s, "
                + "readTimeout=%s, scrapeTimeout=%s, maxMarkdownChars=%d]")
                .formatted(enabled, StringUtils.hasText(apiKey) ? "***" : "(none)", baseUrl, searchLimit,
                        connectTimeout, readTimeout, scrapeTimeout, maxMarkdownChars);
    }
}
