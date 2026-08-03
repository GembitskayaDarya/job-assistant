package com.darya.jobassistant.candidates.runtime;

/**
 * Thrown by {@link PersistentCandidateProfileProvider#getProfile()} when no persistent Candidate
 * Profile aggregate exists for the configured {@code profileKey}. Framework-free, matching {@link
 * com.darya.jobassistant.candidates.aggregate.CandidateProfileConcurrentModificationException}'s
 * convention at this boundary - deliberately not a {@code
 * com.darya.jobassistant.exception.NotFoundException}, since that hierarchy is for REST-facing
 * feature lookups, not startup/runtime-provider configuration failures.
 *
 * <p>Carries only the requested {@code profileKey} and recovery guidance - never profile field
 * values, skill/language lists, or salary information.
 */
public class CandidateProfileNotConfiguredException extends RuntimeException {

    public CandidateProfileNotConfiguredException(String profileKey) {
        super("No persistent Candidate Profile exists for profileKey '" + profileKey
                + "'. Run the explicit Candidate Profile migration (candidate-profile.migration.mode=APPLY) "
                + "before starting normal runtime.");
    }
}
