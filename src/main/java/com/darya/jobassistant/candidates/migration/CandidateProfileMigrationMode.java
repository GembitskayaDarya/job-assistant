package com.darya.jobassistant.candidates.migration;

/**
 * How {@link CandidateProfileMigrationUseCase} should treat a freshly mapped source profile.
 * Deliberately has no {@code OFF} value - that is runner/configuration-only (see {@code
 * candidate-profile.migration.mode} and its own {@code OFF}/{@code DRY_RUN}/{@code APPLY}
 * property values in {@code candidates.migration} runner config); this use case is only ever
 * invoked to actually do one of these two things.
 */
public enum CandidateProfileMigrationMode {

    /** Plan and report only - never calls {@code CandidateProfileRepositoryPort.save}. */
    DRY_RUN,

    /** Creates the profile if missing, or reports {@code NO_OP}/{@code CONFLICT} - never overwrites. */
    APPLY
}
