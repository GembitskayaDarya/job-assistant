package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingResult;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingService;
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
 * Plain-Mockito behavioral tests, mirroring {@code JobDiscoverySchedulerTest}'s convention, plus
 * reflection-based wiring checks for the {@code @Scheduled}/{@code @SchedulerLock} annotations.
 * Real cross-node locking behavior belongs to a real-PostgreSQL concurrency test (not written for
 * this scheduler since the ShedLock mechanism itself is already proven cluster-wide by {@code
 * JobDiscoverySchedulerConcurrencyTest} - this class only proves this scheduler's own trigger
 * wiring and failure-isolation behavior).
 */
@ExtendWith(MockitoExtension.class)
class VacancyRecommendationSchedulerTest {

    @Mock
    private VacancyRecommendationProcessingService processingService;

    private VacancyRecommendationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VacancyRecommendationScheduler(processingService);
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @Test
    void runScheduledProcessing_callsProcessPendingExactlyOnce() {
        when(processingService.processPending()).thenReturn(result());

        scheduler.runScheduledProcessing();

        verify(processingService, times(1)).processPending();
    }

    @Test
    void runScheduledProcessing_neverCallsProcessPendingTwice() {
        when(processingService.processPending()).thenReturn(result());

        scheduler.runScheduledProcessing();

        verify(processingService, times(1)).processPending();
    }

    @Test
    void runScheduledProcessing_unexpectedRuntimeException_isSwallowedAndDoesNotDisableFutureInvocations() {
        when(processingService.processPending())
                .thenThrow(new RuntimeException("unexpected failure"))
                .thenReturn(result());

        assertThatCode(() -> scheduler.runScheduledProcessing()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.runScheduledProcessing()).doesNotThrowAnyException();

        verify(processingService, times(2)).processPending();
    }

    @Test
    void runScheduledProcessing_failure_neverCompensatesWithAnExtraCall() {
        when(processingService.processPending()).thenThrow(new RuntimeException("boom"));

        scheduler.runScheduledProcessing();

        verify(processingService, times(1)).processPending();
    }

    @Test
    void runScheduledProcessing_withoutShedLockProxy_assertionFails() {
        LockAssert.TestHelper.makeAllAssertsPass(false);

        assertThatThrownBy(() -> scheduler.runScheduledProcessing()).isInstanceOf(IllegalStateException.class);

        verify(processingService, times(0)).processPending();
    }

    @Test
    void scheduler_hasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method method = VacancyRecommendationScheduler.class.getDeclaredMethod("runScheduledProcessing");

        for (Annotation annotation : method.getAnnotations()) {
            assertThat(annotation).isNotInstanceOf(Transactional.class);
        }
    }

    @Test
    void runScheduledProcessingMethod_isPublicAndProxyable() throws NoSuchMethodException {
        Method method = VacancyRecommendationScheduler.class.getMethod("runScheduledProcessing");

        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(VacancyRecommendationScheduler.class.getModifiers())).isFalse();
        assertThat(Modifier.isFinal(method.getModifiers())).isFalse();
    }

    @Test
    void scheduledAnnotation_usesConfiguredDelaysAndDedicatedScheduler() throws NoSuchMethodException {
        Method method = VacancyRecommendationScheduler.class.getMethod("runScheduledProcessing");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${vacancy-recommendation.scheduler.fixed-delay}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${vacancy-recommendation.scheduler.initial-delay}");
        assertThat(scheduled.scheduler()).isEqualTo("vacancyRecommendationTaskScheduler");
    }

    @Test
    void schedulerLockAnnotation_usesStableLockNameAndConfiguredDurations() throws NoSuchMethodException {
        Method method = VacancyRecommendationScheduler.class.getMethod("runScheduledProcessing");
        SchedulerLock schedulerLock = method.getAnnotation(SchedulerLock.class);

        assertThat(schedulerLock).isNotNull();
        assertThat(schedulerLock.name()).isEqualTo("vacancyRecommendationProcessingRun");
        assertThat(schedulerLock.lockAtMostFor()).isEqualTo("${vacancy-recommendation.scheduler.lock-at-most-for}");
        assertThat(schedulerLock.lockAtLeastFor()).isEqualTo("${vacancy-recommendation.scheduler.lock-at-least-for}");
    }

    private VacancyRecommendationProcessingResult result() {
        Instant now = Instant.now();
        return new VacancyRecommendationProcessingResult(
                now, now, Duration.ZERO,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0,
                List.of(), 0);
    }
}
