package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.darya.jobassistant.monitoring.JobMonitoringUseCase;
import com.darya.jobassistant.monitoring.config.JobMonitoringProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link JobMonitoringScheduler}'s conditional activation using
 * {@link ApplicationContextRunner} (the same lightweight, Testcontainers-free strategy already
 * used by {@code CandidateProfilePropertiesTest}) rather than a full {@code @SpringBootTest}, so
 * this never needs a database, Telegram credentials, or a live provider. The test config
 * deliberately omits {@code @EnableScheduling}, so {@code @Scheduled} is never processed and no
 * timer ever fires during these tests - only bean presence/absence is exercised.
 */
class JobMonitoringSchedulerActivationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, JobMonitoringScheduler.class);

    @Test
    void schedulerBean_absentByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(JobMonitoringScheduler.class));
    }

    @Test
    void schedulerBean_absentWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues("job-monitoring.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JobMonitoringScheduler.class));
    }

    @Test
    void schedulerBean_presentWithValidEnabledConfiguration() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> assertThat(context).hasSingleBean(JobMonitoringScheduler.class));
    }

    @Test
    void enabledConfiguration_doesNotInvokeUseCaseDuringContextStartup() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    JobMonitoringUseCase useCase = context.getBean(JobMonitoringUseCase.class);
                    verify(useCase, never()).monitor(any());
                });
    }

    private String[] enabledProperties() {
        return new String[] {
                "job-monitoring.enabled=true",
                "job-monitoring.fixed-delay=30m",
                "job-monitoring.initial-delay=1m",
                "job-monitoring.keyword=java backend",
                "job-monitoring.minimum-score=70",
                "job-monitoring.max-notifications=5",
                "job-monitoring.recipient-chat-id=12345"
        };
    }

    @Configuration
    @EnableConfigurationProperties(JobMonitoringProperties.class)
    static class TestConfig {

        @Bean
        JobMonitoringUseCase jobMonitoringUseCase() {
            return mock(JobMonitoringUseCase.class);
        }
    }
}
