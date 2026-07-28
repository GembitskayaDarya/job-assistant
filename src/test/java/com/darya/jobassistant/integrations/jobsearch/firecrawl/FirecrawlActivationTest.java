package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link FirecrawlJobSearchAdapter}/{@link FirecrawlWebClientConfig} conditional
 * activation using {@link ApplicationContextRunner} - the same Testcontainers-free strategy
 * already used by {@code JobMonitoringSchedulerActivationTest} - so this never needs a database
 * or a live Firecrawl account.
 */
class FirecrawlActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, FirecrawlWebClientConfig.class, FirecrawlJobSearchAdapter.class);

    @Test
    void adapterAndClient_absentByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FirecrawlJobSearchAdapter.class);
            assertThat(context).doesNotHaveBean("firecrawlWebClient");
        });
    }

    @Test
    void adapterAndClient_absentWhenExplicitlyDisabled_andNoApiKeyRequired() {
        contextRunner.withPropertyValues("firecrawl.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirecrawlJobSearchAdapter.class);
                    assertThat(context).doesNotHaveBean("firecrawlWebClient");
                });
    }

    @Test
    void adapterAndClient_presentWhenEnabledWithApiKey() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FirecrawlJobSearchAdapter.class);
                    assertThat(context).hasBean("firecrawlWebClient");
                });
    }

    private String[] enabledProperties() {
        return new String[] {
                "firecrawl.enabled=true",
                "firecrawl.api-key=test-key",
                "firecrawl.base-url=https://api.firecrawl.dev",
                "firecrawl.search-limit=10",
                "firecrawl.connect-timeout=5s",
                "firecrawl.read-timeout=30s"
        };
    }

    @Configuration
    @EnableConfigurationProperties(FirecrawlProperties.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
