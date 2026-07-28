package com.darya.jobassistant.jobdiscovery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Same lightweight, Testcontainers-free {@code ApplicationContextRunner} strategy as {@code
 * FirecrawlPropertiesBindingTest} and {@code CandidateProfilePropertiesTest}.
 */
class JobSearchQueryPlanningPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void binding_mapsApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.query-planning.max-queries=5",
                        "job-discovery.query-planning.results-per-query=10",
                        "job-discovery.query-planning.max-skills-per-query=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobSearchQueryPlanningProperties properties = context.getBean(JobSearchQueryPlanningProperties.class);
                    assertThat(properties.maxQueries()).isEqualTo(5);
                    assertThat(properties.resultsPerQuery()).isEqualTo(10);
                    assertThat(properties.maxSkillsPerQuery()).isEqualTo(3);
                });
    }

    @Test
    void binding_mapsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.query-planning.max-queries=8",
                        "job-discovery.query-planning.results-per-query=25",
                        "job-discovery.query-planning.max-skills-per-query=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobSearchQueryPlanningProperties properties = context.getBean(JobSearchQueryPlanningProperties.class);
                    assertThat(properties.maxQueries()).isEqualTo(8);
                    assertThat(properties.resultsPerQuery()).isEqualTo(25);
                    assertThat(properties.maxSkillsPerQuery()).isEqualTo(5);
                });
    }

    @Test
    void binding_zeroMaxQueries_failsContext() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.query-planning.max-queries=0",
                        "job-discovery.query-planning.results-per-query=10",
                        "job-discovery.query-planning.max-skills-per-query=3")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JobSearchQueryPlanningProperties.class)
    static class TestConfig {
    }
}
