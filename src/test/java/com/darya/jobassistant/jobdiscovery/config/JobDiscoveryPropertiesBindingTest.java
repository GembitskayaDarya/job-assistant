package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Same lightweight, Testcontainers-free {@code ApplicationContextRunner} strategy as {@code
 * FirecrawlPropertiesBindingTest} and {@code JobSearchQueryPlanningPropertiesBindingTest}.
 */
class JobDiscoveryPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextStarts_withNoPropertiesSetAtAll() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
            assertThat(properties.enabled()).isFalse();
        });
    }

    @Test
    void binding_mapsApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(disabledDefaultProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
                    assertThat(properties.execution().maxQueriesPerRun()).isEqualTo(3);
                    assertThat(properties.execution().maxScrapesPerRun()).isEqualTo(5);
                    assertThat(properties.execution().maxExtractionsPerRun()).isEqualTo(5);
                    assertThat(properties.execution().maxUniqueReferencesPerRun()).isEqualTo(30);
                    assertThat(properties.execution().maxReportedIssues()).isEqualTo(50);
                    assertThat(properties.budget().monthlyCreditLimit()).isEqualTo(800);
                    assertThat(properties.budget().reserveCredits()).isEqualTo(200);
                    assertThat(properties.budget().maxEstimatedCreditsPerRun()).isEqualTo(15);
                });
    }

    @Test
    void binding_mapsOverriddenValuesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=true",
                        "job-discovery.execution.max-queries-per-run=2",
                        "job-discovery.execution.max-scrapes-per-run=4",
                        "job-discovery.execution.max-extractions-per-run=4",
                        "job-discovery.execution.max-unique-references-per-run=20",
                        "job-discovery.execution.max-reported-issues=25",
                        "job-discovery.budget.monthly-credit-limit=500",
                        "job-discovery.budget.reserve-credits=100",
                        "job-discovery.budget.max-estimated-credits-per-run=20")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.budget().monthlyCreditLimit()).isEqualTo(500);
                    assertThat(properties.budget().reserveCredits()).isEqualTo(100);
                    assertThat(properties.budget().maxEstimatedCreditsPerRun()).isEqualTo(20);
                });
    }

    @Test
    void binding_enabledWithMaxEstimatedCreditsPerRunAboveMonthlyLimit_failsContext() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=true",
                        "job-discovery.execution.max-queries-per-run=3",
                        "job-discovery.execution.max-scrapes-per-run=5",
                        "job-discovery.execution.max-extractions-per-run=5",
                        "job-discovery.execution.max-unique-references-per-run=30",
                        "job-discovery.execution.max-reported-issues=50",
                        "job-discovery.budget.monthly-credit-limit=10",
                        "job-discovery.budget.reserve-credits=0",
                        "job-discovery.budget.max-estimated-credits-per-run=15")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithoutBudget_failsContext() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=true",
                        "job-discovery.execution.max-queries-per-run=3",
                        "job-discovery.execution.max-scrapes-per-run=5",
                        "job-discovery.execution.max-extractions-per-run=5",
                        "job-discovery.execution.max-unique-references-per-run=30",
                        "job-discovery.execution.max-reported-issues=50")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] disabledDefaultProperties() {
        return new String[] {
                "job-discovery.enabled=false",
                "job-discovery.execution.max-queries-per-run=3",
                "job-discovery.execution.max-scrapes-per-run=5",
                "job-discovery.execution.max-extractions-per-run=5",
                "job-discovery.execution.max-unique-references-per-run=30",
                "job-discovery.execution.max-reported-issues=50",
                "job-discovery.budget.monthly-credit-limit=800",
                "job-discovery.budget.reserve-credits=200",
                "job-discovery.budget.max-estimated-credits-per-run=15"
        };
    }

    @EnableConfigurationProperties(JobDiscoveryProperties.class)
    static class TestConfig {
    }
}
