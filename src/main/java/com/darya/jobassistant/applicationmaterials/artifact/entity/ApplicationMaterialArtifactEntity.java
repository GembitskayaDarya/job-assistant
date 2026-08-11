package com.darya.jobassistant.applicationmaterials.artifact.entity;

import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 10 Step 4 persistence foundation for one {@link
 * com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact} (V24)
 * - write-once per natural key, so (like {@code ApplicationMaterialGenerationResultEntity}/{@code
 * ApplicationMaterialRenderSnapshotEntity}) does not extend {@code BaseEntity} and has no {@code
 * updated_at} column. Referenced via a unidirectional {@code @ManyToOne} to {@link
 * ApplicationMaterialGenerationEntity}; database {@code ON DELETE CASCADE} (V24) removes these rows
 * when their generation is deleted.
 */
@Entity
@Table(name = "application_material_artifact")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApplicationMaterialArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false)
    private ApplicationMaterialGenerationEntity generation;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private ApplicationMaterialType materialType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationMaterialFormat format;

    @Column(name = "renderer_version", nullable = false)
    private int rendererVersion;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
