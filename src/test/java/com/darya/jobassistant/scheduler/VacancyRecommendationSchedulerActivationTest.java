package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.darya.jobassistant.config.VacancyRecommendationTaskSchedulerConfig;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Verifies {@link VacancyRecommendationScheduler}/{@link VacancyRecommendationTaskSchedulerConfig}
 * conditional activation using {@link ApplicationContextRunner} - mirrors {@code
 * JobDiscoverySchedulerActivationTest}'s convention exactly. {@code @EnableScheduling} is
 * deliberately omitted, so {@code @Scheduled} is never processed and no timer ever fires during
 * these tests - only bean presence/absence and constructor wiring are exercised.
 */
class VacancyRecommendationSchedulerActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VacancyRecommendationScheduler.class, VacancyRecommendationTaskSchedulerConfig.class);

    @Test
    void schedulerAndTaskSchedulerBeans_absentByDefault() {
        contextRunner.withUserConfiguration(ProcessingServiceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(VacancyRecommendationScheduler.class);
            assertThat(context).doesNotHaveBean("vacancyRecommendationTaskScheduler");
        });
    }

    @Test
    void schedulerAndTaskSchedulerBeans_absentWhenExplicitlyDisabled() {
        contextRunner.withUserConfiguration(ProcessingServiceConfig.class)
                .withPropertyValues("vacancy-recommendation.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VacancyRecommendationScheduler.class);
                    assertThat(context).doesNotHaveBean("vacancyRecommendationTaskScheduler");
                });
    }

    @Test
    void schedulerAndTaskSchedulerBeans_presentWhenEnabled() {
        contextRunner.withUserConfiguration(ProcessingServiceConfig.class)
                .withPropertyValues("vacancy-recommendation.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VacancyRecommendationScheduler.class);
                    assertThat(context).hasBean("vacancyRecommendationTaskScheduler");
                });
    }

    @Test
    void taskScheduler_hasExactlyOneWorkerThread() {
        contextRunner.withUserConfiguration(ProcessingServiceConfig.class)
                .withPropertyValues("vacancy-recommendation.scheduler.enabled=true")
                .run(context -> {
                    ThreadPoolTaskScheduler scheduler =
                            (ThreadPoolTaskScheduler) context.getBean("vacancyRecommendationTaskScheduler");
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
                });
    }

    @Test
    void schedulerEnabled_withoutProcessingServicePrerequisite_failsStartupClearly() {
        contextRunner.withPropertyValues("vacancy-recommendation.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("VacancyRecommendationProcessingService");
                });
    }

    @Test
    void schedulerEnabled_doesNotInvokeProcessingServiceDuringContextStartup() {
        contextRunner.withUserConfiguration(ProcessingServiceConfig.class)
                .withPropertyValues("vacancy-recommendation.scheduler.enabled=true")
                .run(context -> {
                    VacancyRecommendationProcessingService service = context.getBean(VacancyRecommendationProcessingService.class);
                    verify(service, never()).processPending();
                });
    }

    @Configuration
    static class ProcessingServiceConfig {

        @Bean
        VacancyRecommendationProcessingService vacancyRecommendationProcessingService() {
            return mock(VacancyRecommendationProcessingService.class);
        }
    }
}
