package com.darya.jobassistant.applicationmaterials.artifact.repository;

import com.darya.jobassistant.applicationmaterials.artifact.entity.ApplicationMaterialArtifactEntity;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link ApplicationMaterialArtifactEntity} - Sprint 10 Step 4.
 * {@code ApplicationMaterialArtifactRepositoryPort}/{@code ApplicationMaterialArtifactRepositoryAdapter}
 * are the only intended callers.
 */
public interface ApplicationMaterialArtifactRepository extends JpaRepository<ApplicationMaterialArtifactEntity, UUID> {

    Optional<ApplicationMaterialArtifactEntity> findByGenerationIdAndMaterialTypeAndFormatAndRendererVersion(
            UUID generationId, ApplicationMaterialType materialType, ApplicationMaterialFormat format, int rendererVersion);

    List<ApplicationMaterialArtifactEntity> findByGenerationId(UUID generationId);
}
