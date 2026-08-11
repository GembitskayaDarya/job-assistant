package com.darya.jobassistant.applicationmaterials.artifact.persistence;

import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.artifact.entity.ApplicationMaterialArtifactEntity;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import java.time.Instant;

/**
 * Stateless entity &lt;-&gt; domain mapping for {@link ApplicationMaterialArtifact}. No repository
 * calls, no transaction management - {@link ApplicationMaterialArtifactRepositoryAdapter} owns
 * both, matching {@code CareerHistoryPersistenceMapper}'s fully-static convention: unlike the
 * render-snapshot/generation-result mappers, this one needs no JSON (de)serialization, so no
 * injected collaborator is required.
 */
final class ApplicationMaterialArtifactPersistenceMapper {

    private ApplicationMaterialArtifactPersistenceMapper() {
    }

    static ApplicationMaterialArtifact toDomain(ApplicationMaterialArtifactEntity entity) {
        return new ApplicationMaterialArtifact(
                entity.getId(),
                entity.getGeneration().getId(),
                entity.getMaterialType(),
                entity.getFormat(),
                entity.getRendererVersion(),
                entity.getStorageKey(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getSha256Checksum(),
                entity.getCreatedAt());
    }

    /** Only called for a brand-new artifact ({@link ApplicationMaterialArtifact#id()} is {@code null}) - artifacts are write-once per natural key. */
    static ApplicationMaterialArtifactEntity toNewEntity(
            ApplicationMaterialArtifact artifact, ApplicationMaterialGenerationEntity generation, Instant createdAt) {
        return ApplicationMaterialArtifactEntity.builder()
                .generation(generation)
                .materialType(artifact.materialType())
                .format(artifact.format())
                .rendererVersion(artifact.rendererVersion())
                .storageKey(artifact.storageKey())
                .fileName(artifact.fileName())
                .contentType(artifact.contentType())
                .sizeBytes(artifact.sizeBytes())
                .sha256Checksum(artifact.sha256Checksum())
                .createdAt(createdAt)
                .build();
    }
}
