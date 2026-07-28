package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Direct, Spring-context-free unit tests for {@link VacancyCanonicalUrlBackfillRunner}'s
 * DRY_RUN-vs-APPLY dispatch and failure-visibility behavior - {@link
 * VacancyCanonicalUrlBackfillRunnerActivationTest} covers conditional bean creation separately.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCanonicalUrlBackfillRunnerTest {

    @Mock
    private VacancyCanonicalUrlBackfillService backfillService;

    @Test
    void runBackfill_modeDryRun_invokesDryRunOnly() {
        when(backfillService.dryRun()).thenReturn(new VacancyCanonicalUrlBackfillResult(
                VacancyCanonicalUrlBackfillMode.DRY_RUN, 0, 0, 0, 0, 0, 0, 0, false, 0));
        VacancyCanonicalUrlBackfillRunner runner = runner(VacancyCanonicalUrlBackfillMode.DRY_RUN);

        runner.runBackfill();

        verify(backfillService, times(1)).dryRun();
        verify(backfillService, never()).apply();
    }

    @Test
    void runBackfill_modeApply_invokesApplyOnly() {
        when(backfillService.apply()).thenReturn(new VacancyCanonicalUrlBackfillResult(
                VacancyCanonicalUrlBackfillMode.APPLY, 1, 1, 1, 0, 0, 0, 0, true, 1));
        VacancyCanonicalUrlBackfillRunner runner = runner(VacancyCanonicalUrlBackfillMode.APPLY);

        runner.runBackfill();

        verify(backfillService, times(1)).apply();
        verify(backfillService, never()).dryRun();
    }

    @Test
    void runBackfill_dryRunThrows_isLoggedAndSwallowed_neverPropagates() {
        when(backfillService.dryRun()).thenThrow(new RuntimeException("db hiccup"));
        VacancyCanonicalUrlBackfillRunner runner = runner(VacancyCanonicalUrlBackfillMode.DRY_RUN);

        runner.runBackfill();

        verify(backfillService).dryRun();
    }

    @Test
    void runBackfill_applyBlocked_isRethrown_notSwallowed() {
        when(backfillService.apply()).thenThrow(new VacancyCanonicalUrlBackfillBlockedException(
                "blocked", 1, 0, 0, List.of(UUID.randomUUID())));
        VacancyCanonicalUrlBackfillRunner runner = runner(VacancyCanonicalUrlBackfillMode.APPLY);

        assertThatThrownBy(runner::runBackfill).isInstanceOf(VacancyCanonicalUrlBackfillBlockedException.class);
    }

    @Test
    void runBackfill_applyInvariantViolation_isRethrown_notSwallowed() {
        when(backfillService.apply()).thenThrow(new VacancyCanonicalUrlBackfillInvariantViolationException("rolled back"));
        VacancyCanonicalUrlBackfillRunner runner = runner(VacancyCanonicalUrlBackfillMode.APPLY);

        assertThatThrownBy(runner::runBackfill).isInstanceOf(VacancyCanonicalUrlBackfillInvariantViolationException.class);
    }

    private VacancyCanonicalUrlBackfillRunner runner(VacancyCanonicalUrlBackfillMode mode) {
        VacancyCanonicalUrlBackfillProperties properties = new VacancyCanonicalUrlBackfillProperties(true, mode, 500);
        return new VacancyCanonicalUrlBackfillRunner(backfillService, properties);
    }
}
