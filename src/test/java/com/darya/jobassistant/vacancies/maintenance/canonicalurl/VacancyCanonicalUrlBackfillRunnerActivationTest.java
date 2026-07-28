package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link VacancyCanonicalUrlBackfillRunner}'s conditional activation the same way {@code
 * VacancyCanonicalUrlAuditRunnerActivationTest} verifies its own runner - {@link
 * ApplicationContextRunner} never publishes {@code ApplicationReadyEvent} on a bare context
 * refresh, so these tests only ever exercise bean presence/absence, never a real backfill run. See
 * {@link VacancyCanonicalUrlBackfillRunnerTest} for DRY_RUN-vs-APPLY dispatch, tested directly
 * without a Spring context.
 */
class VacancyCanonicalUrlBackfillRunnerActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, VacancyCanonicalUrlBackfillRunner.class);

    @Test
    void runnerBean_absentByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(VacancyCanonicalUrlBackfillRunner.class));
    }

    @Test
    void runnerBean_absentWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("vacancy-canonical-url-backfill.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(VacancyCanonicalUrlBackfillRunner.class));
    }

    @Test
    void runnerBean_presentWithValidEnabledConfiguration() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> assertThat(context).hasSingleBean(VacancyCanonicalUrlBackfillRunner.class));
    }

    @Test
    void enabledConfiguration_doesNotInvokeTheBackfillDuringContextStartup() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    VacancyCanonicalUrlBackfillService backfillService = context.getBean(VacancyCanonicalUrlBackfillService.class);
                    verify(backfillService, never()).dryRun();
                    verify(backfillService, never()).apply();
                });
    }

    private String[] enabledProperties() {
        return new String[] {
                "vacancy-canonical-url-backfill.enabled=true",
                "vacancy-canonical-url-backfill.mode=DRY_RUN",
                "vacancy-canonical-url-backfill.batch-size=500"
        };
    }

    @Configuration
    @EnableConfigurationProperties(VacancyCanonicalUrlBackfillProperties.class)
    static class TestConfig {

        @Bean
        VacancyCanonicalUrlBackfillService vacancyCanonicalUrlBackfillService() {
            return mock(VacancyCanonicalUrlBackfillService.class);
        }
    }
}
