package com.darya.jobassistant.careerhistory.config;

/**
 * Sprint 9 Step 7: the {@code career-history.import.mode} property's allowed values - runner
 * activation/configuration only, distinct from {@code
 * com.darya.jobassistant.careerhistory.importing.CareerHistoryImportMode} (which the use case
 * itself accepts and has no {@code OFF} value at all, since it is only ever invoked to actually do
 * one of the other two things). Mirrors {@code CandidateProfileMigrationRunnerMode}'s convention.
 */
public enum CareerHistoryImportRunnerMode {

    /** The default: no import source or runner bean exists, and normal runtime never reads a career history source file. */
    OFF,

    DRY_RUN,

    APPLY
}
