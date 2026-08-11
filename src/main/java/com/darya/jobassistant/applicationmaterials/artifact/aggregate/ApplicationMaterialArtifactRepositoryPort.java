package com.darya.jobassistant.applicationmaterials.artifact.aggregate;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 10 Step 4: application/domain-facing persistence port for {@link
 * ApplicationMaterialArtifact} - backed by V24's {@code application_material_artifact} table.
 * Returns and accepts {@link ApplicationMaterialArtifact} only - never a JPA entity, never a Spring
 * Data type, never a local filesystem {@code Path}.
 */
public interface ApplicationMaterialArtifactRepositoryPort {

    /**
     * Loads the one artifact for the exact natural key (generation, material type, format,
     * renderer version) - or {@link Optional#empty()} if that combination has not been rendered
     * yet.
     */
    Optional<ApplicationMaterialArtifact> find(
            UUID generationId, ApplicationMaterialType materialType, ApplicationMaterialFormat format, int rendererVersion);

    /** Loads every artifact recorded for {@code generationId}, across every material/format/renderer version. */
    List<ApplicationMaterialArtifact> findByGenerationId(UUID generationId);

    /**
     * Persists {@code artifact} as a brand-new row - {@link ApplicationMaterialArtifact#id()} must
     * be {@code null} (an artifact row is write-once per natural key; there is no update path).
     *
     * @return the persisted artifact, with its durable id and {@code createdAt}
     * @throws IllegalArgumentException if {@code artifact.id()} is already set
     * @throws ApplicationMaterialArtifactAlreadyExistsException if an artifact already exists for
     *     {@code artifact}'s exact natural key - the caller must reload and reuse the existing one
     *     rather than treat this as a hard failure, since a concurrent request may have legitimately
     *     won the same race
     * @throws com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException
     *     if {@code artifact.generationId()} references no existing generation
     */
    ApplicationMaterialArtifact save(ApplicationMaterialArtifact artifact);
}
