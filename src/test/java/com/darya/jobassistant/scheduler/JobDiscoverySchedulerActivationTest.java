package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.darya.jobassistant.config.JobDiscoveryTaskSchedulerConfig;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Verifies {@link JobDiscoveryScheduler}/{@link JobDiscoveryTaskSchedulerConfig} conditional
 * activation using {@link ApplicationContextRunner} - the same Testcontainers-free strategy
 * already used by {@code JobMonitoringSchedulerActivationTest}/{@code
 * JobDiscoveryActivationTest} - so this never needs a database, ShedLock, or a live provider.
 * {@code @EnableScheduling} is deliberately omitted, so {@code @Scheduled} is never processed and
 * no timer ever fires during these tests - only bean presence/absence and constructor wiring are
 * exercised.
 */
class JobDiscoverySchedulerActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JobDiscoveryScheduler.class, JobDiscoveryTaskSchedulerConfig.class);

    @Test
    void schedulerAndTaskSchedulerBeans_absentByDefault() {
        contextRunner.withUserConfiguration(JobDiscoveryServiceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobDiscoveryScheduler.class);
            assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class);
            assertThat(context).doesNotHaveBean("jobDiscoveryTaskScheduler");
        });
    }

    @Test
    void schedulerAndTaskSchedulerBeans_absentWhenExplicitlyDisabled() {
        contextRunner.withUserConfiguration(JobDiscoveryServiceConfig.class)
                .withPropertyValues("job-discovery.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JobDiscoveryScheduler.class);
                    assertThat(context).doesNotHaveBean("jobDiscoveryTaskScheduler");
                });
    }

    @Test
    void schedulerAndTaskSchedulerBeans_presentWhenEnabled() {
        contextRunner.withUserConfiguration(JobDiscoveryServiceConfig.class)
                .withPropertyValues("job-discovery.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JobDiscoveryScheduler.class);
                    assertThat(context).hasBean("jobDiscoveryTaskScheduler");
                });
    }

    @Test
    void taskScheduler_hasExactlyOneWorkerThread() {
        contextRunner.withUserConfiguration(JobDiscoveryServiceConfig.class)
                .withPropertyValues("job-discovery.scheduler.enabled=true")
                .run(context -> {
                    ThreadPoolTaskScheduler scheduler =
                            (ThreadPoolTaskScheduler) context.getBean("jobDiscoveryTaskScheduler");
                    // getPoolSize() reports the live (lazily-started) thread count, which stays 0
                    // until a task actually runs - the core pool size configured via setPoolSize(1)
                    // is the correct, deterministic way to assert "exactly one worker thread".
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                });
    }

    @Test
    void schedulerEnabled_withoutJobDiscoveryServicePrerequisite_failsStartupClearly() {
        contextRunner.withPropertyValues("job-discovery.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JobDiscoveryService");
                });
    }

    @Test
    void schedulerEnabled_doesNotInvokeJobDiscoveryServiceDuringContextStartup() {
        contextRunner.withUserConfiguration(JobDiscoveryServiceConfig.class)
                .withPropertyValues("job-discovery.scheduler.enabled=true")
                .run(context -> {
                    JobDiscoveryService service = context.getBean(JobDiscoveryService.class);
                    verify(service, never()).runDiscovery();
                });
    }

    @Configuration
    static class JobDiscoveryServiceConfig {

        @Bean
        JobDiscoveryService jobDiscoveryService() {
            return mock(JobDiscoveryService.class);
        }
    }
}
