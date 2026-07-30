package com.darya.jobassistant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A dedicated, single-thread {@link ThreadPoolTaskScheduler} used only by {@code
 * VacancyRecommendationScheduler} - never the project-wide {@code taskScheduler} bean shared by
 * {@code JobMonitoringScheduler}/{@code VacancyImportExpirationJob}/{@code VacancyIngestionJob},
 * nor {@code jobDiscoveryTaskScheduler} (see {@code JobDiscoveryTaskSchedulerConfig}, Sprint 8 Step
 * 9). Recommendation processing makes sequential AI and Telegram calls per batch, which must never
 * block - or be blocked by - any other scheduled job in this application.
 *
 * <p>Pool size 1 prevents same-JVM overlap between two local processing invocations on its own
 * (defense in depth); the PostgreSQL ShedLock lock (see {@code ShedLockConfig}, lock name {@code
 * vacancyRecommendationProcessingRun}) remains the actual cross-node correctness mechanism, and the
 * task claim/lease remains the crash-recovery mechanism independent of either.
 *
 * <p>Conditional on {@code vacancy-recommendation.scheduler.enabled=true} - the same flag that
 * activates {@code VacancyRecommendationScheduler} itself - so no dedicated thread exists at all
 * while the scheduler stays off. Spring manages this bean's lifecycle normally (initialize on
 * startup, shutdown on context close) since {@code ThreadPoolTaskScheduler} implements {@code
 * InitializingBean}/{@code DisposableBean} - no manual executor lifecycle management is needed.
 */
@Configuration
@ConditionalOnProperty(prefix = "vacancy-recommendation.scheduler", name = "enabled", havingValue = "true")
public class VacancyRecommendationTaskSchedulerConfig {

    private static final String THREAD_NAME_PREFIX = "vacancy-recommendation-scheduler-";

    @Bean
    public ThreadPoolTaskScheduler vacancyRecommendationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        return scheduler;
    }
}
