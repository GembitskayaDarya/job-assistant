package com.darya.jobassistant.applicationmaterials.render.snapshot.repository;

import com.darya.jobassistant.applicationmaterials.render.snapshot.entity.ApplicationMaterialRenderSnapshotEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link ApplicationMaterialRenderSnapshotEntity} - Sprint 10
 * Step 4. {@code ApplicationMaterialRenderSnapshotRepositoryPort}/{@code
 * ApplicationMaterialRenderSnapshotRepositoryAdapter} are the only intended callers.
 */
public interface ApplicationMaterialRenderSnapshotRepository extends JpaRepository<ApplicationMaterialRenderSnapshotEntity, UUID> {

    Optional<ApplicationMaterialRenderSnapshotEntity> findByGenerationId(UUID generationId);
}
