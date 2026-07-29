package com.darya.jobassistant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A dedicated, single-thread {@link ThreadPoolTaskScheduler} used only by {@code
 * JobDiscoveryScheduler} - never the project-wide {@code taskScheduler} bean that {@code
 * JobMonitoringScheduler}/{@code VacancyImportExpirationJob}/{@code VacancyIngestionJob} share via
 * Spring Boot's default task-scheduling auto-configuration. A long-running discovery call (Search,
 * Scrape, AI extraction, persistence, all sequential) must never occupy the slot those other
 * schedulers depend on; conversely, discovery must never be delayed by them.
 *
 * <p>Pool size 1 is deliberate and sufficient: it prevents same-JVM overlap between two local
 * discovery invocations on its own (defense in depth), while the PostgreSQL ShedLock lock (see
 * {@link ShedLockConfig}) remains the actual cross-node correctness mechanism.
 *
 * <p>Conditional on {@code job-discovery.scheduler.enabled=true} - the same flag that activates
 * {@code JobDiscoveryScheduler} itself - so no dedicated thread is created at all while the
 * scheduler stays off. Spring manages this bean's lifecycle normally: {@link
 * ThreadPoolTaskScheduler#initialize()} runs on startup and {@link
 * ThreadPoolTaskScheduler#shutdown()} runs on context close, both invoked automatically because
 * {@code ThreadPoolTaskScheduler} implements {@code InitializingBean}/{@code DisposableBean} - no
 * manual executor lifecycle management is needed here.
 */
@Configuration
@ConditionalOnProperty(prefix = "job-discovery.scheduler", name = "enabled", havingValue = "true")
public class JobDiscoveryTaskSchedulerConfig {

    private static final String THREAD_NAME_PREFIX = "job-discovery-scheduler-";

    @Bean
    public ThreadPoolTaskScheduler jobDiscoveryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        return scheduler;
    }
}
