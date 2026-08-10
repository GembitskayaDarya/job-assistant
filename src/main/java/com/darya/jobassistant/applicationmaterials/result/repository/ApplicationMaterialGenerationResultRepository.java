package com.darya.jobassistant.applicationmaterials.result.repository;

import com.darya.jobassistant.applicationmaterials.result.entity.ApplicationMaterialGenerationResultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link ApplicationMaterialGenerationResultEntity} - Sprint 10
 * Step 3. {@code ApplicationMaterialGenerationResultRepositoryPort}/{@code
 * ApplicationMaterialGenerationResultRepositoryAdapter} are the only intended callers.
 */
public interface ApplicationMaterialGenerationResultRepository extends JpaRepository<ApplicationMaterialGenerationResultEntity, UUID> {

    Optional<ApplicationMaterialGenerationResultEntity> findByGenerationId(UUID generationId);
}
