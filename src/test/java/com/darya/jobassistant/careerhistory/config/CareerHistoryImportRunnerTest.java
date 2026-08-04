package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.careerhistory.importing.CareerHistoryDiff;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportMode;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportParityException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportResult;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSource;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSourceException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportStatus;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportUseCase;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportValidationException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportViolation;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Proves {@link CareerHistoryImportRunner}'s DRY_RUN/APPLY dispatch and failure-handling behavior
 * with mocked collaborators - no Spring context, no database, mirroring {@code
 * CandidateProfileMigrationRunnerTest}'s convention of testing the plain, constructor-injected
 * class directly. Invoked via {@link CareerHistoryImportRunner#run} - the real {@code
 * ApplicationRunner} contract method - not a bespoke method name, matching how Spring Boot itself
 * calls it.
 */
class CareerHistoryImportRunnerTest {

    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments();

    private final CareerHistoryImportUseCase useCase = mock(CareerHistoryImportUseCase.class);
    private final CareerHistoryImportSource source = mock(CareerHistoryImportSource.class);

    @Test
    void modeOff_doesNothing() {
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.OFF);

        runner.run(NO_ARGS);

        assertThat(mockingDetails(useCase).getInvocations()).isEmpty();
        assertThat(mockingDetails(source).getInvocations()).isEmpty();
    }

    @Test
    void modeDryRun_callsUseCaseDryRunExactlyOnce_neverApply() {
        CareerHistoryImportDocument document = document();
        when(source.load()).thenReturn(document);
        when(useCase.dryRun(document)).thenReturn(dryRunResult());
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.DRY_RUN);

        runner.run(NO_ARGS);

        verify(useCase, times(1)).dryRun(document);
        long applyInvocations = mockingDetails(useCase).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("apply"))
                .count();
        assertThat(applyInvocations).isZero();
    }

    @Test
    void modeApply_callsUseCaseApplyExactlyOnce() {
        CareerHistoryImportDocument document = document();
        when(source.load()).thenReturn(document);
        when(useCase.apply(document)).thenReturn(applyResult());
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.APPLY);

        runner.run(NO_ARGS);

        verify(useCase, times(1)).apply(document);
    }

    @Test
    void modeDryRun_sourceFailure_isSwallowed_notRethrown() {
        when(source.load()).thenThrow(new CareerHistoryImportSourceException("boom"));
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.DRY_RUN);

        runner.run(NO_ARGS);
    }

    @Test
    void modeDryRun_useCaseFailure_isSwallowed_notRethrown() {
        when(source.load()).thenReturn(document());
        when(useCase.dryRun(any())).thenThrow(new CareerHistoryImportValidationException(
                List.of(new CareerHistoryImportViolation("schemaVersion", "REQUIRED", "required"))));
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.DRY_RUN);

        runner.run(NO_ARGS);
    }

    @Test
    void modeApply_sourceFailure_isRethrown_notSwallowed() {
        when(source.load()).thenThrow(new CareerHistoryImportSourceException("boom"));
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.APPLY);

        assertThatThrownBy(() -> runner.run(NO_ARGS)).isInstanceOf(CareerHistoryImportSourceException.class);
    }

    @Test
    void modeApply_useCaseFailure_isRethrown_notSwallowed() {
        when(source.load()).thenReturn(document());
        when(useCase.apply(any())).thenThrow(new CareerHistoryImportParityException("primary"));
        CareerHistoryImportRunner runner = runner(CareerHistoryImportRunnerMode.APPLY);

        assertThatThrownBy(() -> runner.run(NO_ARGS)).isInstanceOf(CareerHistoryImportParityException.class);
    }

    private CareerHistoryImportRunner runner(CareerHistoryImportRunnerMode mode) {
        return new CareerHistoryImportRunner(useCase, source, new CareerHistoryImportProperties(mode, null, 5_242_880L));
    }

    private CareerHistoryImportDocument document() {
        return new CareerHistoryImportDocument(1, "primary", null, List.of());
    }

    private CareerHistoryImportResult dryRunResult() {
        return new CareerHistoryImportResult(CareerHistoryImportMode.DRY_RUN, CareerHistoryImportStatus.WOULD_CREATE,
                "primary", "fingerprint", null, null, null, new CareerHistoryDiff(false, List.of(), 0));
    }

    private CareerHistoryImportResult applyResult() {
        return new CareerHistoryImportResult(CareerHistoryImportMode.APPLY, CareerHistoryImportStatus.CREATED,
                "primary", "fingerprint", null, null, 0L, new CareerHistoryDiff(false, List.of(), 0));
    }
}
