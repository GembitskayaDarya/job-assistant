package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link FirecrawlJobSearchAdapter}/{@link FirecrawlJobPageFetchAdapter}/{@link
 * FirecrawlJobDiscoveryBudgetAdapter}/{@link FirecrawlWebClientConfig} conditional activation
 * using {@link ApplicationContextRunner} - the same Testcontainers-free strategy already used by
 * {@code JobMonitoringSchedulerActivationTest} - so this never needs a database or a live
 * Firecrawl account.
 */
class FirecrawlActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, FirecrawlWebClientConfig.class,
                    FirecrawlJobSearchAdapter.class, FirecrawlJobPageFetchAdapter.class,
                    FirecrawlJobDiscoveryBudgetAdapter.class);

    @Test
    void adaptersAndClient_absentByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FirecrawlJobSearchAdapter.class);
            assertThat(context).doesNotHaveBean(FirecrawlJobPageFetchAdapter.class);
            assertThat(context).doesNotHaveBean(FirecrawlJobDiscoveryBudgetAdapter.class);
            assertThat(context).doesNotHaveBean("firecrawlWebClient");
        });
    }

    @Test
    void adaptersAndClient_absentWhenExplicitlyDisabled_andNoApiKeyRequired() {
        contextRunner.withPropertyValues("firecrawl.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(FirecrawlJobSearchAdapter.class);
                    assertThat(context).doesNotHaveBean(FirecrawlJobPageFetchAdapter.class);
                    assertThat(context).doesNotHaveBean(FirecrawlJobDiscoveryBudgetAdapter.class);
                    assertThat(context).doesNotHaveBean("firecrawlWebClient");
                });
    }

    @Test
    void adaptersAndClient_presentWhenEnabledWithApiKey() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FirecrawlJobSearchAdapter.class);
                    assertThat(context).hasSingleBean(FirecrawlJobPageFetchAdapter.class);
                    assertThat(context).hasSingleBean(FirecrawlJobDiscoveryBudgetAdapter.class);
                    assertThat(context).hasBean("firecrawlWebClient");
                });
    }

    @Test
    void budgetAdapter_absentByDefault_evenWithoutApiKeyRequired() {
        // Test 65: API key remains unnecessary when Firecrawl is disabled - covered together with
        // the other two adapters above since all three share the same activation condition and
        // the same required FirecrawlProperties.api-key precondition once enabled.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FirecrawlJobDiscoveryBudgetAdapter.class);
        });
    }

    private String[] enabledProperties() {
        return new String[] {
                "firecrawl.enabled=true",
                "firecrawl.api-key=test-key",
                "firecrawl.base-url=https://api.firecrawl.dev",
                "firecrawl.search-limit=10",
                "firecrawl.connect-timeout=5s",
                "firecrawl.read-timeout=65s",
                "firecrawl.scrape-timeout=60s",
                "firecrawl.max-markdown-chars=100000",
                "firecrawl.credit-usage-timeout=10s",
                "firecrawl.cost.search-results-per-credit-block=10",
                "firecrawl.cost.search-credits-per-block=2",
                "firecrawl.cost.basic-scrape-credits=1"
        };
    }

    @Configuration
    @EnableConfigurationProperties(FirecrawlProperties.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JobDiscoveryProperties jobDiscoveryProperties() {
            return new JobDiscoveryProperties(true,
                    new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50),
                    new JobDiscoveryProperties.Budget(800, 200, 15),
                    new JobDiscoveryProperties.Scheduler(false, "0 0 8 * * *", "Europe/Warsaw",
                            Duration.ofHours(2), Duration.ofMinutes(1)));
        }
    }
}
