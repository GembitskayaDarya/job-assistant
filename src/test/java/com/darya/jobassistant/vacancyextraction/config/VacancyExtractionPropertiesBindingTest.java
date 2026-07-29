package com.darya.jobassistant.vacancyextraction.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binds {@link VacancyExtractionProperties} the same lightweight, Testcontainers-free way as
 * {@code FirecrawlPropertiesBindingTest} - and, unlike Firecrawl's properties, this binds and
 * validates unconditionally: there is no {@code enabled} flag, since guided manual import (which
 * has nothing to do with Firecrawl) always needs a bound, valid extraction content cap.
 */
class VacancyExtractionPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void binding_mapsApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=40000",
                        "vacancy-extraction.tail-input-chars=10000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyExtractionProperties properties = context.getBean(VacancyExtractionProperties.class);
                    assertThat(properties.maxInputChars()).isEqualTo(40_000);
                    assertThat(properties.tailInputChars()).isEqualTo(10_000);
                });
    }

    @Test
    void binding_mapsOverriddenValues() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=5000",
                        "vacancy-extraction.tail-input-chars=1000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyExtractionProperties properties = context.getBean(VacancyExtractionProperties.class);
                    assertThat(properties.maxInputChars()).isEqualTo(5000);
                    assertThat(properties.tailInputChars()).isEqualTo(1000);
                });
    }

    @Test
    void binding_isIndependentOfFirecrawlEnabled() {
        contextRunner
                .withPropertyValues(
                        "firecrawl.enabled=false",
                        "vacancy-extraction.max-input-chars=40000",
                        "vacancy-extraction.tail-input-chars=10000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyExtractionProperties properties = context.getBean(VacancyExtractionProperties.class);
                    assertThat(properties.maxInputChars()).isEqualTo(40_000);
                });
    }

    @Test
    void binding_nonPositiveMaxInputChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=0",
                        "vacancy-extraction.tail-input-chars=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_excessiveMaxInputChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=100001",
                        "vacancy-extraction.tail-input-chars=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_negativeTailInputChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=1000",
                        "vacancy-extraction.tail-input-chars=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_tailInputCharsNotLessThanMaxInputChars_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-extraction.max-input-chars=1000",
                        "vacancy-extraction.tail-input-chars=1000")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(VacancyExtractionProperties.class)
    static class TestConfig {
    }
}
