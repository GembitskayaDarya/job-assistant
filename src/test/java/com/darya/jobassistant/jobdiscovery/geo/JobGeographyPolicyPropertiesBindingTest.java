package com.darya.jobassistant.jobdiscovery.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Same lightweight, Testcontainers-free {@code ApplicationContextRunner} strategy as {@code
 * JobDiscoveryPropertiesBindingTest}.
 */
class JobGeographyPolicyPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextStarts_withNoPropertiesSetAtAll() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            JobGeographyPolicyProperties properties = context.getBean(JobGeographyPolicyProperties.class);
            assertThat(properties.acceptedRegionTerms()).isEmpty();
            assertThat(properties.incompatibleRegionTerms()).isEmpty();
        });
    }

    @Test
    void binding_mapsCommaDelimitedTerms() {
        contextRunner
                .withPropertyValues(
                        "job-discovery.geography.accepted-region-terms=Poland,Europe,Remote Europe",
                        "job-discovery.geography.incompatible-region-terms=US only,UK only")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JobGeographyPolicyProperties properties = context.getBean(JobGeographyPolicyProperties.class);
                    assertThat(properties.acceptedRegionTerms()).containsExactly("Poland", "Europe", "Remote Europe");
                    assertThat(properties.incompatibleRegionTerms()).containsExactly("US only", "UK only");
                });
    }

    @EnableConfigurationProperties(JobGeographyPolicyProperties.class)
    static class TestConfig {
    }
}
