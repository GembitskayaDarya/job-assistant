package com.darya.jobassistant.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FirecrawlPropertiesTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(65);
    private static final Duration SCRAPE_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_MARKDOWN_CHARS = 100_000;
    private static final Duration CREDIT_USAGE_TIMEOUT = Duration.ofSeconds(10);
    private static final FirecrawlProperties.Cost COST = new FirecrawlProperties.Cost(10, 2, 1);

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireOtherFieldsToBeValid() {
        assertThatCode(() -> properties(false, null, null, 0, null, null, null, 0)).doesNotThrowAnyException();
        assertThatCode(() -> new FirecrawlProperties(false, null, null, 0, null, null, null, 0, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsBlankApiKey() {
        assertThatThrownBy(() -> properties(true, "  ", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullApiKey() {
        assertThatThrownBy(() -> properties(true, null, "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> properties(true, "test-key", " ", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveSearchLimit() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 0, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsSearchLimitAbove100() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 101, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsSearchLimitOfOne() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 1, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsSearchLimitOf100() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 100, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullConnectTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, null,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveConnectTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, Duration.ZERO,
                READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullReadTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                null, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveReadTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ZERO, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullScrapeTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, null, MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsScrapeTimeoutBelowOneSecond() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, Duration.ofMillis(999), MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsScrapeTimeoutAbove300Seconds() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ofSeconds(400), Duration.ofSeconds(301), MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsScrapeTimeoutOfOneSecond() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ofSeconds(2), Duration.ofSeconds(1), MAX_MARKDOWN_CHARS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_acceptsScrapeTimeoutOf300Seconds() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ofSeconds(301), Duration.ofSeconds(300), MAX_MARKDOWN_CHARS))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveMaxMarkdownChars() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsExcessiveMaxMarkdownChars() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, 1_000_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxMarkdownCharsUpperBound() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                READ_TIMEOUT, SCRAPE_TIMEOUT, 1_000_000))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsReadTimeoutEqualToScrapeTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ofSeconds(60), Duration.ofSeconds(60), MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsReadTimeoutBelowScrapeTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT,
                Duration.ofSeconds(30), Duration.ofSeconds(60), MAX_MARKDOWN_CHARS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_masksApiKey() {
        FirecrawlProperties properties = properties(true, "super-secret-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS);

        assertThatCode(() -> {
            String rendered = properties.toString();
            if (rendered.contains("super-secret-key")) {
                throw new AssertionError("toString() must not expose the raw api key: " + rendered);
            }
        }).doesNotThrowAnyException();
    }

    // --- credit-usage-timeout ---------------------------------------------------------------

    @Test
    void enabled_rejectsNullCreditUsageTimeout() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, null, COST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsCreditUsageTimeoutBelowOneSecond() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, Duration.ofMillis(999), COST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsCreditUsageTimeoutAbove30Seconds() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, Duration.ofSeconds(31), COST))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsCreditUsageTimeoutBoundaries() {
        assertThatCode(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, Duration.ofSeconds(1), COST))
                .doesNotThrowAnyException();
        assertThatCode(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, Duration.ofSeconds(30), COST))
                .doesNotThrowAnyException();
    }

    // --- cost ----------------------------------------------------------------------------------

    @Test
    void enabled_rejectsNullCost() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveSearchResultsPerCreditBlock() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(0, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsSearchResultsPerCreditBlockAbove1000() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(1001, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveSearchCreditsPerBlock() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(10, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsSearchCreditsPerBlockAbove1000() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(10, 1001, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveBasicScrapeCredits() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(10, 2, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBasicScrapeCreditsAbove1000() {
        assertThatThrownBy(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(10, 2, 1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsCostBoundaries() {
        assertThatCode(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(1, 1, 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new FirecrawlProperties(true, "test-key", "https://api.firecrawl.dev", 10,
                CONNECT_TIMEOUT, READ_TIMEOUT, SCRAPE_TIMEOUT, MAX_MARKDOWN_CHARS, CREDIT_USAGE_TIMEOUT,
                new FirecrawlProperties.Cost(1000, 1000, 1000)))
                .doesNotThrowAnyException();
    }

    private FirecrawlProperties properties(boolean enabled, String apiKey, String baseUrl, int searchLimit,
                                            Duration connectTimeout, Duration readTimeout, Duration scrapeTimeout,
                                            int maxMarkdownChars) {
        return new FirecrawlProperties(enabled, apiKey, baseUrl, searchLimit, connectTimeout, readTimeout,
                scrapeTimeout, maxMarkdownChars, CREDIT_USAGE_TIMEOUT, COST);
    }
}
