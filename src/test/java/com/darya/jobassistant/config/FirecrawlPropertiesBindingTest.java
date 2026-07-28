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
                        "firecrawl.read-timeout=30s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FirecrawlProperties properties = context.getBean(FirecrawlProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.apiKey()).isEmpty();
                    assertThat(properties.baseUrl()).isEqualTo("https://api.firecrawl.dev");
                    assertThat(properties.searchLimit()).isEqualTo(10);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
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
                        "firecrawl.read-timeout=1m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FirecrawlProperties properties = context.getBean(FirecrawlProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.apiKey()).isEqualTo("test-key");
                    assertThat(properties.baseUrl()).isEqualTo("https://custom.firecrawl.internal");
                    assertThat(properties.searchLimit()).isEqualTo(25);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(1));
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
                        "firecrawl.read-timeout=30s")
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
                        "firecrawl.read-timeout=30s")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(FirecrawlProperties.class)
    static class TestConfig {
    }
}
