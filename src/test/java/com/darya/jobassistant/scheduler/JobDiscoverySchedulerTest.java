package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.jobdiscovery.JobDiscoveryOrchestrationException;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryRunResult;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryService;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain-Mockito behavioral tests, mirroring {@code JobMonitoringSchedulerTest}'s convention, plus
 * reflection-based wiring checks for the {@code @Scheduled}/{@code @SchedulerLock} annotations
 * themselves. Real cross-node locking behavior is exercised separately, against a real PostgreSQL
 * database, in {@code JobDiscoverySchedulerConcurrencyTest}.
 *
 * <p>{@link LockAssert#assertLocked()} only passes inside ShedLock's own AOP proxy; {@link
 * LockAssert.TestHelper#makeAllAssertsPass(boolean)} is ShedLock's own documented mechanism for
 * unit-testing a {@code @SchedulerLock} method's body without that proxy - used here for every
 * test except {@link #runScheduledDiscovery_withoutShedLockProxy_assertionFails()}, which
 * deliberately leaves it disabled to prove the tripwire itself still works.
 */
@ExtendWith(MockitoExtension.class)
class JobDiscoverySchedulerTest {

    @Mock
    private JobDiscoveryService jobDiscoveryService;

    private JobDiscoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JobDiscoveryScheduler(jobDiscoveryService);
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void runScheduledDiscovery_callsRunDiscoveryExactlyOnce() {
        when(jobDiscoveryService.runDiscovery()).thenReturn(allowedResult());

        scheduler.runScheduledDiscovery();

        verify(jobDiscoveryService, times(1)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_neverCallsRunDiscoveryTwice() {
        when(jobDiscoveryService.runDiscovery()).thenReturn(allowedResult());

        scheduler.runScheduledDiscovery();

        verify(jobDiscoveryService, times(1)).runDiscovery();
        // A single invocation of this method must never itself trigger a second run - see the
        // failure-handling tests below for the "no compensating call after failure" guarantee.
    }

    @Test
    void runScheduledDiscovery_budgetDeniedResult_doesNotThrow() {
        when(jobDiscoveryService.runDiscovery()).thenReturn(deniedResult(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT));

        assertThatCode(() -> scheduler.runScheduledDiscovery()).doesNotThrowAnyException();

        verify(jobDiscoveryService, times(1)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_budgetUnavailableResult_remainsFailClosed_noExtraAction() {
        JobDiscoveryBudgetDecision unavailable = JobDiscoveryBudgetDecision.unavailable(
                6, 5, 800, 200, 15, "HTTP_500");
        when(jobDiscoveryService.runDiscovery()).thenReturn(resultWithDecision(unavailable));

        assertThatCode(() -> scheduler.runScheduledDiscovery()).doesNotThrowAnyException();

        verify(jobDiscoveryService, times(1)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_orchestrationException_isIsolated() {
        when(jobDiscoveryService.runDiscovery())
                .thenThrow(new JobDiscoveryOrchestrationException("profile unavailable", new RuntimeException("cause")));

        assertThatCode(() -> scheduler.runScheduledDiscovery()).doesNotThrowAnyException();

        verify(jobDiscoveryService, times(1)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_unexpectedRuntimeException_doesNotDisableFutureInvocations() {
        // First invocation fails; the second (simulating the next cron trigger) must still work.
        when(jobDiscoveryService.runDiscovery())
                .thenThrow(new RuntimeException("unexpected failure"))
                .thenReturn(allowedResult());

        assertThatCode(() -> scheduler.runScheduledDiscovery()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.runScheduledDiscovery()).doesNotThrowAnyException();

        verify(jobDiscoveryService, times(2)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_failure_doesNotCallAnyOtherCollaborator() {
        // JobDiscoveryService is the scheduler's only collaborator - a failed run must not reach
        // for any compensating external call, which this constructor's single dependency already
        // makes structurally impossible; this test documents that guarantee explicitly.
        when(jobDiscoveryService.runDiscovery()).thenThrow(new RuntimeException("boom"));

        scheduler.runScheduledDiscovery();

        verify(jobDiscoveryService, times(1)).runDiscovery();
    }

    @Test
    void runScheduledDiscovery_withoutShedLockProxy_assertionFails() {
        LockAssert.TestHelper.makeAllAssertsPass(false);

        assertThatThrownBy(() -> scheduler.runScheduledDiscovery()).isInstanceOf(IllegalStateException.class);

        verify(jobDiscoveryService, times(0)).runDiscovery();
    }

    @Test
    void scheduler_hasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method method = JobDiscoveryScheduler.class.getDeclaredMethod("runScheduledDiscovery");

        for (Annotation annotation : method.getAnnotations()) {
            assertThat(annotation).isNotInstanceOf(Transactional.class);
        }
    }

    @Test
    void runScheduledDiscoveryMethod_isPublicAndProxyable() throws NoSuchMethodException {
        Method method = JobDiscoveryScheduler.class.getMethod("runScheduledDiscovery");

        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(JobDiscoveryScheduler.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(method.getModifiers())).isFalse();
    }

    @Test
    void scheduledAnnotation_usesConfiguredCronZoneAndDedicatedScheduler() throws NoSuchMethodException {
        Method method = JobDiscoveryScheduler.class.getMethod("runScheduledDiscovery");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${job-discovery.scheduler.cron}");
        assertThat(scheduled.zone()).isEqualTo("${job-discovery.scheduler.zone}");
        assertThat(scheduled.scheduler()).isEqualTo("jobDiscoveryTaskScheduler");
    }

    @Test
    void schedulerLockAnnotation_usesStableLockNameAndConfiguredDurations() throws NoSuchMethodException {
        Method method = JobDiscoveryScheduler.class.getMethod("runScheduledDiscovery");
        SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);

        assertThat(schedulerLock).isNotNull();
        assertThat(schedulerLock.name()).isEqualTo("jobDiscoveryDailyRun");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("${job-discovery.scheduler.lock-at-most-for}");
        assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("${job-discovery.scheduler.lock-at-least-for}");
    }

    // --- Fixtures --------------------------------------------------------------------------------

    private JobDiscoveryRunResult allowedResult() {
        JobDiscoveryBudgetDecision allowed = new JobDiscoveryBudgetDecision(JobDiscoveryBudgetStatus.ALLOWED, true,
                6, 5, 11, 800, 200, 15,
                1000L, 1000L, 0L, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"), null);
        return resultWithDecision(allowed);
    }

    private JobDiscoveryRunResult deniedResult(JobDiscoveryBudgetStatus status) {
        JobDiscoveryBudgetDecision denied = new JobDiscoveryBudgetDecision(status, false,
                20, 5, 25, 800, 200, 15,
                1000L, 1000L, 0L, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"), null);
        return resultWithDecision(denied);
    }

    private JobDiscoveryRunResult resultWithDecision(JobDiscoveryBudgetDecision decision) {
        Instant now = Instant.now();
        return new JobDiscoveryRunResult(
                now, now, Duration.ZERO,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                false, false, false, false,
                0, 0, 0,
                0, 0, 0,
                0, 0, false,
                0, 0, 0,
                List.of(), List.of(), 0,
                decision);
    }
}
