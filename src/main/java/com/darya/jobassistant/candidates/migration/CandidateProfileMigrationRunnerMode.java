package com.darya.jobassistant.candidates.migration;

/**
 * The {@code candidate-profile.migration.mode} property's allowed values - runner
 * activation/configuration only, distinct from {@link CandidateProfileMigrationMode} (which the
 * use case itself accepts and has no {@code OFF} value at all, since it is only ever invoked to
 * actually do one of the other two things).
 */
public enum CandidateProfileMigrationRunnerMode {

    /** The default: {@link CandidateProfileMigrationRunner} does nothing. */
    OFF,

    DRY_RUN,

    APPLY
}
