package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.util.UUID;

/**
 * Thrown by {@code ApplicationMaterialsCandidateContextProvider} when the current Candidate
 * Profile/Career History no longer matches the versions an {@code ApplicationMaterialGeneration}
 * recorded when it was requested - i.e. the source data has moved on since the generation was
 * created. This is a fail-safe, not a historical-snapshot mechanism: neither Candidate Profile nor
 * Career History repositories store version history, so an old version can never be reloaded - a
 * mismatch always means "stop, do not generate against different data than what the generation
 * recorded," never "load the old revision instead."
 *
 * <p>Carries only safe identifiers - generation id and the compared version numbers - never any
 * candidate-profile or career-history content, matching {@code
 * CareerHistoryConcurrentModificationException}'s "safe identifiers only" convention.
 */
public class CandidateContextVersionMismatchException extends RuntimeException {

    /** Which specific version-consistency check failed - see {@link #reason()}. */
    public enum Reason {

        /** The current Candidate Profile version no longer matches what the generation recorded. */
        CANDIDATE_PROFILE_VERSION_CHANGED,

        /** The generation recorded a Career History version, but Career History no longer exists. */
        CAREER_HISTORY_NOW_ABSENT,

        /** The generation recorded no Career History, but one now exists. */
        CAREER_HISTORY_NOW_PRESENT,

        /** Both the generation and the current state have a Career History, but at different versions. */
        CAREER_HISTORY_VERSION_CHANGED
    }

    private final Reason reason;
    private final UUID generationId;
    private final long expectedCandidateProfileVersion;
    private final long actualCandidateProfileVersion;
    private final Long expectedCareerHistoryVersion;
    private final Long actualCareerHistoryVersion;

    public CandidateContextVersionMismatchException(
            Reason reason,
            UUID generationId,
            long expectedCandidateProfileVersion,
            long actualCandidateProfileVersion,
            Long expectedCareerHistoryVersion,
            Long actualCareerHistoryVersion) {
        super(buildMessage(reason, generationId, expectedCandidateProfileVersion, actualCandidateProfileVersion,
                expectedCareerHistoryVersion, actualCareerHistoryVersion));
        this.reason = reason;
        this.generationId = generationId;
        this.expectedCandidateProfileVersion = expectedCandidateProfileVersion;
        this.actualCandidateProfileVersion = actualCandidateProfileVersion;
        this.expectedCareerHistoryVersion = expectedCareerHistoryVersion;
        this.actualCareerHistoryVersion = actualCareerHistoryVersion;
    }

    public Reason reason() {
        return reason;
    }

    public UUID generationId() {
        return generationId;
    }

    public long expectedCandidateProfileVersion() {
        return expectedCandidateProfileVersion;
    }

    public long actualCandidateProfileVersion() {
        return actualCandidateProfileVersion;
    }

    public Long expectedCareerHistoryVersion() {
        return expectedCareerHistoryVersion;
    }

    public Long actualCareerHistoryVersion() {
        return actualCareerHistoryVersion;
    }

    private static String buildMessage(
            Reason reason, UUID generationId, long expectedCandidateProfileVersion, long actualCandidateProfileVersion,
            Long expectedCareerHistoryVersion, Long actualCareerHistoryVersion) {
        return "Application material generation '" + generationId + "' candidate context is stale (" + reason
                + ") - expected candidateProfileVersion=" + expectedCandidateProfileVersion
                + ", actual=" + actualCandidateProfileVersion
                + "; expected careerHistoryVersion=" + expectedCareerHistoryVersion
                + ", actual=" + actualCareerHistoryVersion;
    }
}
