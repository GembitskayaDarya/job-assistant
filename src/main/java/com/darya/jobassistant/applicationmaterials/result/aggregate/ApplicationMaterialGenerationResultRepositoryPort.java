package com.darya.jobassistant.applicationmaterials.result.aggregate;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 10 Step 3: application/domain-facing persistence port for {@link
 * ApplicationMaterialGenerationResult} - backed by V22's {@code
 * application_material_generation_result} table. Returns and accepts {@link
 * ApplicationMaterialGenerationResult} only - never a JPA entity, never a Spring Data type, never a
 * raw JSON string.
 */
public interface ApplicationMaterialGenerationResultRepositoryPort {

    /**
     * Loads the one result for {@code generationId}, or {@link Optional#empty()} if that
     * generation has none yet.
     */
    Optional<ApplicationMaterialGenerationResult> findByGenerationId(UUID generationId);

    /**
     * Persists {@code result} as a brand-new row - {@link ApplicationMaterialGenerationResult#id()}
     * must be {@code null} (results are write-once; there is no update path - see {@link
     * ApplicationMaterialGenerationResult#create}).
     *
     * @return the persisted result, with its durable id and {@code createdAt}
     * @throws IllegalArgumentException if {@code result.id()} is already set
     * @throws ApplicationMaterialGenerationResultAlreadyExistsException if {@code
     *     result.generationId()} already has a result
     * @throws com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException
     *     if {@code result.generationId()} references no existing generation
     */
    ApplicationMaterialGenerationResult save(ApplicationMaterialGenerationResult result);
}
