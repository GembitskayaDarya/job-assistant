package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.darya.jobassistant.vacancyimport.ExpireVacancyImportSessionsUseCase;
import com.darya.jobassistant.vacancyimport.config.VacancyImportCleanupProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link VacancyImportExpirationJob}'s conditional activation and configuration binding
 * using {@link ApplicationContextRunner} (the same lightweight, Testcontainers-free strategy
 * {@code JobMonitoringSchedulerActivationTest} uses) rather than a full {@code @SpringBootTest} -
 * no database, Telegram credentials, or live provider needed. The test config deliberately omits
 * {@code @EnableScheduling}, so {@code @Scheduled} is never processed and no timer ever fires
 * during these tests - only bean presence/absence and property binding are exercised.
 */
class VacancyImportExpirationJobActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, VacancyImportExpirationJob.class);

    @Test
    void schedulerBean_presentByDefaultWithNoPropertiesSet() {
        // Cleanup is enabled by default (matchIfMissing = true): unlike job-monitoring/ingestion,
        // it only touches this application's own database, so it is safe out of the box.
        contextRunner.run(context -> assertThat(context).hasSingleBean(VacancyImportExpirationJob.class));
    }

    @Test
    void schedulerBean_absentWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("vacancy-import.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(VacancyImportExpirationJob.class));
    }

    @Test
    void schedulerBean_presentWithExplicitlyEnabledConfiguration() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> assertThat(context).hasSingleBean(VacancyImportExpirationJob.class));
    }

    @Test
    void enabledConfiguration_doesNotInvokeUseCaseDuringContextStartup() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    ExpireVacancyImportSessionsUseCase useCase = context.getBean(ExpireVacancyImportSessionsUseCase.class);
                    verify(useCase, never()).expireBatch();
                });
    }

    @Test
    void configurationValues_bindCorrectlyFromProperties() {
        contextRunner.withPropertyValues(
                        "vacancy-import.cleanup.enabled=true",
                        "vacancy-import.cleanup.fixed-delay=2h",
                        "vacancy-import.cleanup.initial-delay=90s",
                        "vacancy-import.cleanup.batch-size=42")
                .run(context -> {
                    VacancyImportCleanupProperties properties = context.getBean(VacancyImportCleanupProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.fixedDelay()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.initialDelay()).isEqualTo(Duration.ofSeconds(90));
                    assertThat(properties.batchSize()).isEqualTo(42);
                });
    }

    @Test
    void invalidBatchSize_whenEnabled_failsContextStartupClearly() {
        contextRunner.withPropertyValues(
                        "vacancy-import.cleanup.enabled=true",
                        "vacancy-import.cleanup.fixed-delay=1h",
                        "vacancy-import.cleanup.initial-delay=1m",
                        "vacancy-import.cleanup.batch-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void invalidFixedDelay_whenEnabled_failsContextStartupClearly() {
        contextRunner.withPropertyValues(
                        "vacancy-import.cleanup.enabled=true",
                        "vacancy-import.cleanup.fixed-delay=0s",
                        "vacancy-import.cleanup.initial-delay=1m",
                        "vacancy-import.cleanup.batch-size=100")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    private String[] enabledProperties() {
        return new String[] {
                "vacancy-import.cleanup.enabled=true",
                "vacancy-import.cleanup.fixed-delay=1h",
                "vacancy-import.cleanup.initial-delay=1m",
                "vacancy-import.cleanup.batch-size=100"
        };
    }

    @Configuration
    @EnableConfigurationProperties(VacancyImportCleanupProperties.class)
    static class TestConfig {

        @Bean
        ExpireVacancyImportSessionsUseCase expireVacancyImportSessionsUseCase() {
            return mock(ExpireVacancyImportSessionsUseCase.class);
        }
    }
}
