package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingResult;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin infrastructure trigger for one bounded {@link
 * VacancyRecommendationProcessingService#processPending()} call per scheduled occurrence - no
 * business logic of its own, matching {@code JobDiscoveryScheduler}'s convention (Sprint 8 Step 9).
 *
 * <p>Same two-layer overlap protection as {@code JobDiscoveryScheduler}: {@code scheduler =
 * "vacancyRecommendationTaskScheduler"} runs this method on its own dedicated single-thread {@code
 * TaskScheduler} (see {@code VacancyRecommendationTaskSchedulerConfig}), and {@code
 * @SchedulerLock} acquires the PostgreSQL-backed distributed lock {@value #LOCK_NAME} before the
 * method body runs at all - the actual cross-node correctness mechanism. If another instance
 * already holds it, this invocation is skipped entirely: zero AI/Telegram/persistence calls, since
 * {@link VacancyRecommendationProcessingService#processPending()} is never even entered.
 *
 * <p>{@link LockAssert#assertLocked()} is a diagnostic tripwire: it fails loudly if this method is
 * ever invoked without going through ShedLock's AOP proxy, which would otherwise silently defeat
 * cross-node protection.
 *
 * <p>Requiring {@link VacancyRecommendationProcessingService} as a mandatory constructor
 * dependency is deliberate, mirroring {@code JobDiscoveryScheduler}'s requirement on {@code
 * JobDiscoveryService}: that service only exists as a bean when {@code
 * vacancy-recommendation.enabled=true} and its own Job Analysis/Telegram infrastructure is present,
 * so enabling the scheduler without those fails application startup with Spring's own clear "no
 * qualifying bean" message rather than this class being wired with nothing to call.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "vacancy-recommendation.scheduler", name = "enabled", havingValue = "true")
public class VacancyRecommendationScheduler {

    static final String LOCK_NAME = "vacancyRecommendationProcessingRun";

    private final VacancyRecommendationProcessingService processingService;

    public VacancyRecommendationScheduler(VacancyRecommendationProcessingService processingService) {
        this.processingService = processingService;
    }

    @Scheduled(
            fixedDelayString = "${vacancy-recommendation.scheduler.fixed-delay}",
            initialDelayString = "${vacancy-recommendation.scheduler.initial-delay}",
            scheduler = "vacancyRecommendationTaskScheduler"
    )
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "${vacancy-recommendation.scheduler.lock-at-most-for}",
            lockAtLeastFor = "${vacancy-recommendation.scheduler.lock-at-least-for}"
    )
    public void runScheduledProcessing() {
        LockAssert.assertLocked();
        try {
            VacancyRecommendationProcessingResult result = processingService.processPending();
            log.info("Scheduled vacancy recommendation processing completed - claimedTasks: {}, completedTasks: {}, "
                            + "notifiedTasks: {}, retryScheduled: {}, deadTasks: {}, duration: {}",
                    result.claimedTasks(), result.completedTasks(), result.notifiedTasks(),
                    result.retryScheduled(), result.deadTasks(), result.duration());
        } catch (RuntimeException e) {
            // Logged once, here, at this scheduler's own outer boundary, then swallowed so the
            // next trigger still fires - never converted into a successful result and never
            // compensated with an extra call.
            log.error("Scheduled vacancy recommendation processing failed - category=SCHEDULER_RUN_FAILED, "
                    + "exceptionType={} - will resume on the next scheduled invocation", e.getClass().getSimpleName(), e);
        }
    }
}
