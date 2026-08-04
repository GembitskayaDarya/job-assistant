package com.darya.jobassistant.careerhistory.importing;

/**
 * How {@link CareerHistoryImportUseCase} should treat a freshly mapped source document.
 * Deliberately has no {@code OFF} value - that is runner/configuration-only (see {@code
 * com.darya.jobassistant.careerhistory.config.CareerHistoryImportRunnerMode}); this use case is
 * only ever invoked to actually do one of these two things. Mirrors {@code
 * CandidateProfileMigrationMode}'s convention.
 */
public enum CareerHistoryImportMode {

    /** Plan and report only - never calls {@code CareerHistoryRepositoryPort.save}. */
    DRY_RUN,

    /** Creates/updates when appropriate, or reports {@code NO_OP}/{@code CONFLICT} - never overwrites without a matching expected version. */
    APPLY
}
