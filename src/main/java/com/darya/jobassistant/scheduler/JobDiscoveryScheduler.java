package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.jobdiscovery.JobDiscoveryRunResult;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryService;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin infrastructure trigger for one bounded {@link JobDiscoveryService#runDiscovery()} call per
 * scheduled occurrence - no business logic of its own, matching {@code
 * JobMonitoringScheduler}/{@code VacancyImportExpirationJob}'s convention.
 *
 * <p>Two independent, layered overlap protections apply:
 * <ul>
 *   <li>{@code scheduler = "jobDiscoveryTaskScheduler"} runs this method on a dedicated, single
 *       -thread {@code TaskScheduler} (see {@code JobDiscoveryTaskSchedulerConfig}), so a second
 *       local firing while one run is still in progress cannot even be dispatched concurrently in
 *       this JVM;
 *   <li>{@code @SchedulerLock} acquires a PostgreSQL-backed distributed lock (see {@code
 *       ShedLockConfig}) named {@value #LOCK_NAME} before the method body runs at all, which is the
 *       actual cross-node correctness mechanism - if another instance already holds it, this
 *       invocation is skipped entirely (zero budget/Search/Scrape/AI/persistence calls) rather than
 *       waiting or retrying.
 * </ul>
 *
 * <p>{@link LockAssert#assertLocked()} is a diagnostic tripwire, not part of the locking mechanism
 * itself: it fails loudly if this method is ever somehow invoked without going through ShedLock's
 * AOP proxy (e.g. a future refactor that starts calling {@link #runScheduledDiscovery()} directly),
 * which would otherwise silently defeat cross-node protection.
 *
 * <p>Requiring {@link JobDiscoveryService} as a mandatory constructor dependency is deliberate,
 * mirroring {@code JobMonitoringScheduler}'s requirement on {@code JobMonitoringUseCase}: {@code
 * JobDiscoveryService} only exists as a bean when {@code job-discovery.enabled=true} and its own
 * port/budget infrastructure is present, so enabling the scheduler without those fails application
 * startup with Spring's own clear "no qualifying bean" message rather than this class being wired
 * with nothing to call.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "job-discovery.scheduler", name = "enabled", havingValue = "true")
public class JobDiscoveryScheduler {

    static final String LOCK_NAME = "jobDiscoveryDailyRun";

    private final JobDiscoveryService jobDiscoveryService;

    public JobDiscoveryScheduler(JobDiscoveryService jobDiscoveryService) {
        this.jobDiscoveryService = jobDiscoveryService;
    }

    @Scheduled(
            cron = "${job-discovery.scheduler.cron}",
            zone = "${job-discovery.scheduler.zone}",
            scheduler = "jobDiscoveryTaskScheduler"
    )
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "${job-discovery.scheduler.lock-at-most-for}",
            lockAtLeastFor = "${job-discovery.scheduler.lock-at-least-for}"
    )
    public void runScheduledDiscovery() {
        LockAssert.assertLocked();
        try {
            JobDiscoveryRunResult result = jobDiscoveryService.runDiscovery();
            logSummary(result);
        } catch (RuntimeException e) {
            // Covers both JobDiscoveryOrchestrationException (unrecoverable profile/setup failure)
            // and any other unexpected RuntimeException the same way: logged once, here, at this
            // scheduler's own outer boundary, then swallowed so the next cron trigger still fires.
            // Never converted into a successful result and never compensated with an extra call.
            log.error("Scheduled job discovery run failed - category=SCHEDULER_RUN_FAILED, exceptionType={} - "
                    + "will resume on the next scheduled invocation", e.getClass().getSimpleName(), e);
        }
    }

    private void logSummary(JobDiscoveryRunResult result) {
        JobDiscoveryBudgetDecision decision = result.budgetDecision();
        if (decision.status() == JobDiscoveryBudgetStatus.UNAVAILABLE) {
            log.warn("Scheduled job discovery run completed - budgetStatus: {}, estimatedTotalCredits: {}, "
                            + "duration: {}",
                    decision.status(), decision.estimatedTotalCredits(), result.duration());
            return;
        }
        if (decision.status() != JobDiscoveryBudgetStatus.ALLOWED) {
            log.info("Scheduled job discovery run completed - budgetStatus: {}, estimatedTotalCredits: {}, "
                            + "duration: {}",
                    decision.status(), decision.estimatedTotalCredits(), result.duration());
            return;
        }
        log.info("Scheduled job discovery run completed - budgetStatus: {}, plannedQueries: {}, "
                        + "executedQueries: {}, failedQueries: {}, discoveredReferences: {}, "
                        + "existingVacanciesSkipped: {}, scrapeAttempts: {}, scrapeSuccesses: {}, "
                        + "scrapeFailures: {}, extractionAttempts: {}, extractionSuccesses: {}, "
                        + "extractionFailures: {}, createdVacancies: {}, alreadyExistingAfterRace: {}, "
                        + "persistenceFailures: {}, queryLimitReached: {}, scrapeLimitReached: {}, "
                        + "extractionLimitReached: {}, uniqueReferenceLimitReached: {}, duration: {}",
                decision.status(), result.plannedQueries(), result.executedQueries(), result.failedQueries(),
                result.discoveredReferences(), result.existingVacanciesSkipped(), result.scrapeAttempts(),
                result.scrapeSuccesses(), result.scrapeFailures(), result.extractionAttempts(),
                result.extractionSuccesses(), result.extractionFailures(), result.createdVacancies(),
                result.alreadyExistingAfterRace(), result.persistenceFailures(), result.queryLimitReached(),
                result.scrapeLimitReached(), result.extractionLimitReached(), result.uniqueReferenceLimitReached(),
                result.duration());
    }
}
