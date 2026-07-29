package com.darya.jobassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binds {@link FirecrawlProperties} the same lightweight, Testcontainers-free way as
 * {@code CandidateProfilePropertiesTest} and {@code JobMonitoringSchedulerActivationTest}, so this
 * never needs a database or a live Firecrawl account. Also doubles as the "app starts without
 * FIRECRAWL_API_KEY while disabled" check for the property-binding layer itself.
 */
class FirecrawlPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextStarts_withNoPropertiesSetAtAll() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FirecrawlProperties properties = context.getBean(FirecrawlProperties.class);
            assertThat(properties.enabled()).isFalse();
        });
    }

    @Test
    void binding_mapsApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=false",
                        "firecrawl.api-key=",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FirecrawlProperties properties = context.getBean(FirecrawlProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.apiKey()).isEmpty();
                    assertThat(properties.baseUrl()).isEqualTo("https://api.firecrawl.dev");
                    assertThat(properties.searchLimit()).isEqualTo(10);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(65));
                    assertThat(properties.scrapeTimeout()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.maxMarkdownChars()).isEqualTo(100_000);
                });
    }

    @Test
    void binding_mapsOverriddenValuesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://custom.firecrawl.internal",
                        "firecrawl.search-limit=25",
                        "firecrawl.connect-timeout=2s",
                        "firecrawl.read-timeout=2m",
                        "firecrawl.scrape-timeout=90s",
                        "firecrawl.max-markdown-chars=250000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FirecrawlProperties properties = context.getBean(FirecrawlProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.apiKey()).isEqualTo("test-key");
                    assertThat(properties.baseUrl()).isEqualTo("https://custom.firecrawl.internal");
                    assertThat(properties.searchLimit()).isEqualTo(25);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(properties.scrapeTimeout()).isEqualTo(Duration.ofSeconds(90));
                    assertThat(properties.maxMarkdownChars()).isEqualTo(250_000);
                });
    }

    @Test
    void binding_enabledWithoutApiKey_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithSearchLimitAbove100_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=101",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithScrapeTimeoutBelowOneSecond_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=500ms",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithScrapeTimeoutAbove300Seconds_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=400s",
                        "firecrawl.scrape-timeout=301s",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithNonPositiveMaxMarkdownChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithExcessiveMaxMarkdownChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=65s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=1000001")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithReadTimeoutNotGreaterThanScrapeTimeout_failsContext() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=true",
                        "firecrawl.api-key=test-key",
                        "firecrawl.base-url=https://api.firecrawl.dev",
                        "firecrawl.search-limit=10",
                        "firecrawl.connect-timeout=5s",
                        "firecrawl.read-timeout=60s",
                        "firecrawl.scrape-timeout=60s",
                        "firecrawl.max-markdown-chars=100000")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(FirecrawlProperties.class)
    static class TestConfig {
    }
}
