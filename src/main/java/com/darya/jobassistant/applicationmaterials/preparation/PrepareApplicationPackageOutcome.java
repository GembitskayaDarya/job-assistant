package com.darya.jobassistant.applicationmaterials.preparation;

import java.util.UUID;

/**
 * Sprint 10 Step 5: provider-independent outcome of {@link PrepareApplicationPackageUseCase#prepare}
 * - mirrors {@code AnalyzeVacancyResult}/{@code AnalyzeImportedVacancyResult}'s "report a sealed
 * outcome, don't just throw" convention, since more than one of these is a normal, expected result
 * a caller (a Telegram command or callback) must render safely, not an exceptional one.
 */
public sealed interface PrepareApplicationPackageOutcome {

    /** A generation completed - freshly, or reused - and both PDFs were loaded successfully. */
    record Prepared(PreparedApplicationPackage preparedPackage) implements PrepareApplicationPackageOutcome {

        public Prepared {
            if (preparedPackage == null) {
                throw new IllegalArgumentException("Prepared outcome preparedPackage must not be null");
            }
        }
    }

    /**
     * A matching generation for the same Vacancy and current candidate versions is currently
     * {@code IN_PROGRESS} - either found already in that state, or this call itself lost a
     * concurrent race to start it. No new generation or AI request was started.
     */
    record AlreadyInProgress(UUID generationId) implements PrepareApplicationPackageOutcome {
    }

    /** No Vacancy exists for the requested id. */
    record VacancyNotFound(UUID vacancyId) implements PrepareApplicationPackageOutcome {
    }

    /**
     * Generation, rendering, or artifact loading failed for a controlled, safe-to-report reason -
     * see the use case's log output for the underlying cause. {@code generationId} is present
     * whenever a generation row exists to reference (every case except a candidate-context
     * version race detected before any generation was created).
     */
    record Failed(UUID generationId) implements PrepareApplicationPackageOutcome {
    }
}
