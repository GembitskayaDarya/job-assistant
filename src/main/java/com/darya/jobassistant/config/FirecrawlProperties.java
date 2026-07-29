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
 *
 * <p>{@code creditUsageTimeout} bounds {@code FirecrawlJobDiscoveryBudgetAdapter}'s single {@code
 * GET /v2/team/credit-usage} call - a dedicated, per-operation Reactor {@code timeout()} shorter
 * than {@code readTimeout}, not another transport-level client setting. {@code cost} carries this
 * project's own Firecrawl pricing assumptions (credits per search-result block, per basic-proxy
 * scrape) used only to <em>estimate</em> a run's maximum spend before it starts - nested here,
 * rather than as a second top-level {@code @ConfigurationProperties} record, so Firecrawl pricing
 * knobs stay next to every other Firecrawl-specific setting.
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
        int maxMarkdownChars,
        Duration creditUsageTimeout,
        Cost cost
) {

    private static final int MAX_SEARCH_LIMIT = 100;
    private static final Duration MIN_SCRAPE_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_SCRAPE_TIMEOUT = Duration.ofSeconds(300);
    private static final int MAX_MARKDOWN_CHARS_LIMIT = 1_000_000;
    private static final Duration MIN_CREDIT_USAGE_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_CREDIT_USAGE_TIMEOUT = Duration.ofSeconds(30);
    private static final int MIN_COST_VALUE = 1;
    private static final int MAX_COST_VALUE = 1000;

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
            if (creditUsageTimeout == null
                    || creditUsageTimeout.compareTo(MIN_CREDIT_USAGE_TIMEOUT) < 0
                    || creditUsageTimeout.compareTo(MAX_CREDIT_USAGE_TIMEOUT) > 0) {
                throw new IllegalArgumentException(
                        "firecrawl.credit-usage-timeout must be between " + MIN_CREDIT_USAGE_TIMEOUT + " and "
                                + MAX_CREDIT_USAGE_TIMEOUT + " when firecrawl.enabled=true, but was " + creditUsageTimeout);
            }
            if (cost == null) {
                throw new IllegalArgumentException("firecrawl.cost must be set when firecrawl.enabled=true");
            }
            requireInCostRange(cost.searchResultsPerCreditBlock(), "firecrawl.cost.search-results-per-credit-block");
            requireInCostRange(cost.searchCreditsPerBlock(), "firecrawl.cost.search-credits-per-block");
            requireInCostRange(cost.basicScrapeCredits(), "firecrawl.cost.basic-scrape-credits");
        }
    }

    private static void requireInCostRange(int value, String propertyName) {
        if (value < MIN_COST_VALUE || value > MAX_COST_VALUE) {
            throw new IllegalArgumentException(
                    propertyName + " must be between " + MIN_COST_VALUE + " and " + MAX_COST_VALUE
                            + " when firecrawl.enabled=true, but was " + value);
        }
    }

    @Override
    public String toString() {
        return ("FirecrawlProperties[enabled=%s, apiKey=%s, baseUrl=%s, searchLimit=%d, connectTimeout=%s, "
                + "readTimeout=%s, scrapeTimeout=%s, maxMarkdownChars=%d, creditUsageTimeout=%s, cost=%s]")
                .formatted(enabled, StringUtils.hasText(apiKey) ? "***" : "(none)", baseUrl, searchLimit,
                        connectTimeout, readTimeout, scrapeTimeout, maxMarkdownChars, creditUsageTimeout, cost);
    }

    /**
     * This project's own Firecrawl pricing assumptions, used only to estimate a run's maximum
     * credit spend before it starts - see {@code FirecrawlCreditCostEstimator}. Isolated here
     * (rather than inlined into {@code FirecrawlJobDiscoveryBudgetAdapter} or {@code
     * JobDiscoveryService}) so a future Firecrawl pricing change never touches provider-neutral
     * budget policy ({@code JobDiscoveryBudgetPolicy}, under {@code job-discovery.budget}) or
     * orchestration code.
     */
    public record Cost(int searchResultsPerCreditBlock, int searchCreditsPerBlock, int basicScrapeCredits) {
    }
}
