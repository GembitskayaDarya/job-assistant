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
 * Verifies {@link VacancyCanonicalUrlAuditRunner}'s conditional activation the same way {@code
 * JobMonitoringSchedulerActivationTest} verifies {@code JobMonitoringScheduler}'s - {@link
 * ApplicationContextRunner} never publishes {@code ApplicationReadyEvent} on a bare context
 * refresh (only {@code SpringApplication.run()} does that), so these tests only ever exercise bean
 * presence/absence, never a real audit run.
 */
class VacancyCanonicalUrlAuditRunnerActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, VacancyCanonicalUrlAuditRunner.class);

    @Test
    void runnerBean_absentByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(VacancyCanonicalUrlAuditRunner.class));
    }

    @Test
    void runnerBean_absentWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("vacancy-canonical-url-audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(VacancyCanonicalUrlAuditRunner.class));
    }

    @Test
    void runnerBean_presentWithValidEnabledConfiguration() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> assertThat(context).hasSingleBean(VacancyCanonicalUrlAuditRunner.class));
    }

    @Test
    void enabledConfiguration_doesNotInvokeTheAuditDuringContextStartup() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    VacancyCanonicalUrlAuditService auditService = context.getBean(VacancyCanonicalUrlAuditService.class);
                    verify(auditService, never()).audit();
                });
    }

    private String[] enabledProperties() {
        return new String[] {
                "vacancy-canonical-url-audit.enabled=true",
                "vacancy-canonical-url-audit.batch-size=500",
                "vacancy-canonical-url-audit.max-reported-issues=100"
        };
    }

    @Configuration
    @EnableConfigurationProperties(VacancyCanonicalUrlAuditProperties.class)
    static class TestConfig {

        @Bean
        VacancyCanonicalUrlAuditService vacancyCanonicalUrlAuditService() {
            return mock(VacancyCanonicalUrlAuditService.class);
        }
    }
}
