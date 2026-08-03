package com.darya.jobassistant.candidates.migration;

/**
 * Thrown by {@link CandidateProfileMigrationSource#loadSourceProfile} when the configured source
 * is missing entirely (e.g. {@code candidate-profile.yml} not found - {@code spring.config.import}
 * is {@code optional:}, so a missing file binds every field to {@code null} rather than failing
 * config-data processing) or present but missing required fields. Framework-free, matching {@link
 * com.darya.jobassistant.candidates.aggregate.CandidateProfileConcurrentModificationException}'s
 * convention at this boundary. Carries only a safe, generic message - never file contents or
 * partially-bound field values.
 */
public class CandidateProfileMigrationSourceException extends RuntimeException {

    public CandidateProfileMigrationSourceException(String message) {
        super(message);
    }
}
