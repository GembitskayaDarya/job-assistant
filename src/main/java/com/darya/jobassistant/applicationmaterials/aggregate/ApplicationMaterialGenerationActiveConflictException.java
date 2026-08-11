package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.UUID;

/**
 * Sprint 10 Step 5 (production-readiness acceptance fix): thrown by {@link
 * ApplicationMaterialGenerationRepositoryPort#save} when creating a new generation would leave two
 * simultaneously-active ({@code PENDING}/{@code IN_PROGRESS}) generations for the same {@code
 * vacancyId}/{@code candidateProfileVersion}/{@code careerHistoryVersion} - {@code
 * uk_amg_active_effective_key} (V25) is the actual enforcement, PostgreSQL's own final concurrency
 * authority for this invariant.
 *
 * <p>Expected to be caught by the caller ({@code PrepareApplicationPackageUseCase}) and resolved by
 * reloading and continuing from the generation that won the race, not treated as a hard failure -
 * mirrors {@link com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactAlreadyExistsException}'s
 * "lost a benign concurrent-creation race" convention. Never thrown for a {@code COMPLETED} or
 * {@code FAILED} generation - the unique index only ever covers the two active statuses.
 */
public class ApplicationMaterialGenerationActiveConflictException extends RuntimeException {

    public ApplicationMaterialGenerationActiveConflictException(UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        super("An active application material generation already exists for vacancyId '" + vacancyId
                + "', candidateProfileVersion " + candidateProfileVersion + ", careerHistoryVersion " + careerHistoryVersion);
    }
}
