package com.darya.jobassistant.applicationmaterials.render.snapshot.persistence;

import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.entity.ApplicationMaterialRenderSnapshotEntity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entity &lt;-&gt; domain mapping for {@link ApplicationMaterialRenderSnapshot} - no repository
 * calls, no transaction management; {@link ApplicationMaterialRenderSnapshotRepositoryAdapter} owns
 * both, matching {@code ApplicationMaterialGenerationResultPersistenceMapper}'s convention.
 */
@Component
@RequiredArgsConstructor
class ApplicationMaterialRenderSnapshotPersistenceMapper {

    private final ApplicationMaterialRenderSnapshotContentMapper contentMapper;

    ApplicationMaterialRenderSnapshot toDomain(ApplicationMaterialRenderSnapshotEntity entity) {
        return new ApplicationMaterialRenderSnapshot(
                entity.getId(),
                entity.getGeneration().getId(),
                entity.getSchemaVersion(),
                contentMapper.read(entity.getContent()),
                entity.getCreatedAt());
    }

    /** Only called for a brand-new snapshot ({@link ApplicationMaterialRenderSnapshot#id()} is {@code null}) - snapshots are write-once. */
    ApplicationMaterialRenderSnapshotEntity toNewEntity(
            ApplicationMaterialRenderSnapshot snapshot, ApplicationMaterialGenerationEntity generation, Instant createdAt) {
        return ApplicationMaterialRenderSnapshotEntity.builder()
                .generation(generation)
                .schemaVersion(snapshot.schemaVersion())
                .content(contentMapper.write(snapshot.content()))
                .createdAt(createdAt)
                .build();
    }
}
