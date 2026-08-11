package com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 10 Step 4: application/domain-facing persistence port for {@link
 * ApplicationMaterialRenderSnapshot} - backed by V23's {@code
 * application_material_render_snapshot} table. Returns and accepts {@link
 * ApplicationMaterialRenderSnapshot} only - never a JPA entity, never a Spring Data type, never a
 * raw JSON string.
 */
public interface ApplicationMaterialRenderSnapshotRepositoryPort {

    Optional<ApplicationMaterialRenderSnapshot> findByGenerationId(UUID generationId);

    /**
     * Persists {@code snapshot} as a brand-new row - {@link ApplicationMaterialRenderSnapshot#id()}
     * must be {@code null} (snapshots are write-once; there is no update path - see {@link
     * ApplicationMaterialRenderSnapshot#create}).
     *
     * @return the persisted snapshot, with its durable id and {@code createdAt}
     * @throws IllegalArgumentException if {@code snapshot.id()} is already set
     * @throws ApplicationMaterialRenderSnapshotAlreadyExistsException if {@code
     *     snapshot.generationId()} already has a snapshot - the caller (see {@code
     *     RenderApplicationMaterialsUseCase}) must reload and reuse the existing one rather than
     *     treat this as a hard failure, since a concurrent request may have legitimately won the
     *     same race
     * @throws com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException
     *     if {@code snapshot.generationId()} references no existing generation
     */
    ApplicationMaterialRenderSnapshot save(ApplicationMaterialRenderSnapshot snapshot);
}
