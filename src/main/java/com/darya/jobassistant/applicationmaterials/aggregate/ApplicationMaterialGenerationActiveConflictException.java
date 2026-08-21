package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.UUID;

/**
 * Sprint 10 Step 5 (production-readiness acceptance fix), Sprint 11 production hardening: thrown by
 * {@link ApplicationMaterialGenerationRepositoryPort#save} when creating a new generation would
 * leave two simultaneously-active ({@code PENDING}/{@code IN_PROGRESS}) generations for the same
 * {@code vacancyId}/{@code sourceFingerprint} - {@code uk_amg_active_source_fingerprint} (V31) is the
 * actual enforcement, PostgreSQL's own final concurrency authority for this invariant. Re-keyed from
 * {@code vacancyId}/{@code candidateProfileVersion}/{@code careerHistoryVersion} (the original V25
 * index) to {@code vacancyId}/{@code sourceFingerprint} when {@link
 * ApplicationMaterialGeneration#sourceFingerprint()} became the sole reuse-validity authority - the
 * active-uniqueness concurrency guard must key on exactly the same "effective source state" concept
 * the reuse decision itself uses, or two concurrent requests with genuinely different source content
 * (e.g. one racing against a Personal-Project change the other has not observed yet) could otherwise
 * be incorrectly treated as racing for the same row and one would silently join the other's
 * mismatched-source generation.
 *
 * <p>Expected to be caught by the caller ({@code PrepareApplicationPackageUseCase}) and resolved by
 * reloading and continuing from the generation that won the race, not treated as a hard failure -
 * mirrors {@link com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactAlreadyExistsException}'s
 * "lost a benign concurrent-creation race" convention. Never thrown for a {@code COMPLETED} or
 * {@code FAILED} generation - the unique index only ever covers the two active statuses.
 */
public class ApplicationMaterialGenerationActiveConflictException extends RuntimeException {

    public ApplicationMaterialGenerationActiveConflictException(UUID vacancyId, String sourceFingerprint) {
        super("An active application material generation already exists for vacancyId '" + vacancyId
                + "', sourceFingerprint '" + sourceFingerprint + "'");
    }
}
