package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binds {@link VacancyCanonicalUrlAuditProperties} the same lightweight, Testcontainers-free way
 * as {@code FirecrawlPropertiesBindingTest} and {@code JobMonitoringSchedulerActivationTest}.
 */
class VacancyCanonicalUrlAuditPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextStarts_withNoPropertiesSetAtAll_disabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            VacancyCanonicalUrlAuditProperties properties = context.getBean(VacancyCanonicalUrlAuditProperties.class);
            assertThat(properties.enabled()).isFalse();
        });
    }

    @Test
    void binding_mapsApplicationYmlDefaults() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-audit.enabled=false",
                        "vacancy-canonical-url-audit.batch-size=500",
                        "vacancy-canonical-url-audit.max-reported-issues=100")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyCanonicalUrlAuditProperties properties = context.getBean(VacancyCanonicalUrlAuditProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.batchSize()).isEqualTo(500);
                    assertThat(properties.maxReportedIssues()).isEqualTo(100);
                });
    }

    @Test
    void binding_mapsOverriddenValuesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-audit.enabled=true",
                        "vacancy-canonical-url-audit.batch-size=250",
                        "vacancy-canonical-url-audit.max-reported-issues=20")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyCanonicalUrlAuditProperties properties = context.getBean(VacancyCanonicalUrlAuditProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.batchSize()).isEqualTo(250);
                    assertThat(properties.maxReportedIssues()).isEqualTo(20);
                });
    }

    @Test
    void binding_enabledWithZeroBatchSize_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-audit.enabled=true",
                        "vacancy-canonical-url-audit.batch-size=0",
                        "vacancy-canonical-url-audit.max-reported-issues=100")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithNegativeMaxReportedIssues_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-audit.enabled=true",
                        "vacancy-canonical-url-audit.batch-size=500",
                        "vacancy-canonical-url-audit.max-reported-issues=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(VacancyCanonicalUrlAuditProperties.class)
    static class TestConfig {
    }
}
