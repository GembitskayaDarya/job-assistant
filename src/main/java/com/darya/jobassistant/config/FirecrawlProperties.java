package com.darya.jobassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Configuration for the future Firecrawl-backed {@code JobSearchPort}/{@code JobPageFetchPort}
 * adapters. Validation only runs when {@code enabled=true}, matching {@code JobMonitoringProperties}
 * and {@code VacancyImportCleanupProperties}'s convention: the application must keep starting
 * with no {@code FIRECRAWL_API_KEY} configured as long as Firecrawl stays disabled.
 */
@ConfigurationProperties(prefix = "firecrawl")
public record FirecrawlProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        int searchLimit,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final int MAX_SEARCH_LIMIT = 100;

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
        }
    }

    @Override
    public String toString() {
        return "FirecrawlProperties[enabled=%s, apiKey=%s, baseUrl=%s, searchLimit=%d, connectTimeout=%s, readTimeout=%s]"
                .formatted(enabled, StringUtils.hasText(apiKey) ? "***" : "(none)", baseUrl, searchLimit,
                        connectTimeout, readTimeout);
    }
}
