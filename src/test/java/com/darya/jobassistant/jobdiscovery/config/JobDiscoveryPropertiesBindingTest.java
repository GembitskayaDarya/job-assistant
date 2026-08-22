package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
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
    void binding_listingExpansionFieldsDefaultWhenNotSet() {
        contextRunner
                .withPropertyValues(disabledDefaultProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
                    assertThat(properties.execution().maxListingPagesPerRun()).isEqualTo(2);
                    assertThat(properties.execution().maxCandidateLinksPerListing()).isEqualTo(25);
                    assertThat(properties.execution().minContentCharsForExtraction()).isZero();
                });
    }

    @Test
    void binding_listingExpansionFieldsMapWhenExplicitlySet() {
        String[] base = disabledDefaultProperties();
        String[] withListingFields = Arrays.copyOf(base, base.length + 3);
        withListingFields[base.length] = "job-discovery.execution.max-listing-pages-per-run=4";
        withListingFields[base.length + 1] = "job-discovery.execution.max-candidate-links-per-listing=40";
        withListingFields[base.length + 2] = "job-discovery.execution.min-content-chars-for-extraction=100";

        contextRunner.withPropertyValues(withListingFields).run(context -> {
            assertThat(context).hasNotFailed();
            JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
            assertThat(properties.execution().maxListingPagesPerRun()).isEqualTo(4);
            assertThat(properties.execution().maxCandidateLinksPerListing()).isEqualTo(40);
            assertThat(properties.execution().minContentCharsForExtraction()).isEqualTo(100);
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

    // --- scheduler -------------------------------------------------------------------------------

    @Test
    void binding_mapsSchedulerApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(disabledDefaultPropertiesWithScheduler())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
                    assertThat(properties.scheduler().enabled()).isFalse();
                    assertThat(properties.scheduler().cron()).isEqualTo("0 0 8 * * *");
                    assertThat(properties.scheduler().zone()).isEqualTo("Europe/Warsaw");
                    assertThat(properties.scheduler().lockAtMostFor()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.scheduler().lockAtLeastFor()).isEqualTo(Duration.ofMinutes(1));
                });
    }

    @Test
    void binding_mapsOverriddenSchedulerValuesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=true",
                        "job-discovery.execution.max-queries-per-run=3",
                        "job-discovery.execution.max-scrapes-per-run=5",
                        "job-discovery.execution.max-extractions-per-run=5",
                        "job-discovery.execution.max-unique-references-per-run=30",
                        "job-discovery.execution.max-reported-issues=50",
                        "job-discovery.budget.monthly-credit-limit=800",
                        "job-discovery.budget.reserve-credits=200",
                        "job-discovery.budget.max-estimated-credits-per-run=15",
                        "job-discovery.scheduler.enabled=true",
                        "job-discovery.scheduler.cron=0 30 6 * * *",
                        "job-discovery.scheduler.zone=UTC",
                        "job-discovery.scheduler.lock-at-most-for=90m",
                        "job-discovery.scheduler.lock-at-least-for=2m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobDiscoveryProperties properties = context.getBean(JobDiscoveryProperties.class);
                    assertThat(properties.scheduler().enabled()).isTrue();
                    assertThat(properties.scheduler().cron()).isEqualTo("0 30 6 * * *");
                    assertThat(properties.scheduler().zone()).isEqualTo("UTC");
                    assertThat(properties.scheduler().lockAtMostFor()).isEqualTo(Duration.ofMinutes(90));
                    assertThat(properties.scheduler().lockAtLeastFor()).isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Test
    void binding_schedulerEnabledWithDiscoveryDisabled_failsContext() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=false",
                        "job-discovery.scheduler.enabled=true",
                        "job-discovery.scheduler.cron=0 0 8 * * *",
                        "job-discovery.scheduler.zone=Europe/Warsaw",
                        "job-discovery.scheduler.lock-at-most-for=2h",
                        "job-discovery.scheduler.lock-at-least-for=1m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_schedulerEnabledWithInvalidCron_failsContext() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.enabled=true",
                        "job-discovery.execution.max-queries-per-run=3",
                        "job-discovery.execution.max-scrapes-per-run=5",
                        "job-discovery.execution.max-extractions-per-run=5",
                        "job-discovery.execution.max-unique-references-per-run=30",
                        "job-discovery.execution.max-reported-issues=50",
                        "job-discovery.budget.monthly-credit-limit=800",
                        "job-discovery.budget.reserve-credits=200",
                        "job-discovery.budget.max-estimated-credits-per-run=15",
                        "job-discovery.scheduler.enabled=true",
                        "job-discovery.scheduler.cron=not a cron",
                        "job-discovery.scheduler.zone=Europe/Warsaw",
                        "job-discovery.scheduler.lock-at-most-for=2h",
                        "job-discovery.scheduler.lock-at-least-for=1m")
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] disabledDefaultPropertiesWithScheduler() {
        String[] base = disabledDefaultProperties();
        String[] withScheduler = Arrays.copyOf(base, base.length + 5);
        withScheduler[base.length] = "job-discovery.scheduler.enabled=false";
        withScheduler[base.length + 1] = "job-discovery.scheduler.cron=0 0 8 * * *";
        withScheduler[base.length + 2] = "job-discovery.scheduler.zone=Europe/Warsaw";
        withScheduler[base.length + 3] = "job-discovery.scheduler.lock-at-most-for=2h";
        withScheduler[base.length + 4] = "job-discovery.scheduler.lock-at-least-for=1m";
        return withScheduler;
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
