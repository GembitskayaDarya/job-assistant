package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binds {@link VacancyCanonicalUrlBackfillProperties} the same lightweight, Testcontainers-free
 * way as {@code VacancyCanonicalUrlAuditPropertiesBindingTest} and {@code FirecrawlPropertiesBindingTest}.
 */
class VacancyCanonicalUrlBackfillPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextStarts_withNoPropertiesSetAtAll_disabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            VacancyCanonicalUrlBackfillProperties properties = context.getBean(VacancyCanonicalUrlBackfillProperties.class);
            assertThat(properties.enabled()).isFalse();
        });
    }

    @Test
    void binding_mapsApplicationYmlDefaults_modeDefaultsToDryRun() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-backfill.enabled=false",
                        "vacancy-canonical-url-backfill.mode=DRY_RUN",
                        "vacancy-canonical-url-backfill.batch-size=500")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyCanonicalUrlBackfillProperties properties = context.getBean(VacancyCanonicalUrlBackfillProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.mode()).isEqualTo(VacancyCanonicalUrlBackfillMode.DRY_RUN);
                    assertThat(properties.batchSize()).isEqualTo(500);
                });
    }

    @Test
    void binding_mapsOverriddenValuesWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-backfill.enabled=true",
                        "vacancy-canonical-url-backfill.mode=APPLY",
                        "vacancy-canonical-url-backfill.batch-size=250")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VacancyCanonicalUrlBackfillProperties properties = context.getBean(VacancyCanonicalUrlBackfillProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.mode()).isEqualTo(VacancyCanonicalUrlBackfillMode.APPLY);
                    assertThat(properties.batchSize()).isEqualTo(250);
                });
    }

    @Test
    void binding_enabledWithInvalidModeString_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-backfill.enabled=true",
                        "vacancy-canonical-url-backfill.mode=NOT_A_REAL_MODE",
                        "vacancy-canonical-url-backfill.batch-size=500")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_enabledWithZeroBatchSize_failsContext() {
        contextRunner
                .withPropertyValues(
                        "vacancy-canonical-url-backfill.enabled=true",
                        "vacancy-canonical-url-backfill.mode=DRY_RUN",
                        "vacancy-canonical-url-backfill.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(VacancyCanonicalUrlBackfillProperties.class)
    static class TestConfig {
    }
}
