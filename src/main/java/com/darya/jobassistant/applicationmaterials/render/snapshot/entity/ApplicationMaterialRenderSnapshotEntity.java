package com.darya.jobassistant.applicationmaterials.render.snapshot.entity;

import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Sprint 10 Step 4 persistence foundation for one {@link
 * com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot}
 * (V23) - mirrors {@code ApplicationMaterialGenerationResultEntity}'s (Step 3) conventions exactly:
 * no {@code BaseEntity} (write-once, no {@code updated_at} column), unidirectional {@code @ManyToOne}
 * to {@link ApplicationMaterialGenerationEntity} with database {@code ON DELETE CASCADE} (V23)
 * solely responsible for removal, and {@link #content} as a genuine PostgreSQL {@code jsonb} column
 * via {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain {@link String} field - the actual
 * (de)serialization is owned entirely by {@code ApplicationMaterialRenderSnapshotContentMapper},
 * never this entity.
 */
@Entity
@Table(name = "application_material_render_snapshot")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApplicationMaterialRenderSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false, unique = true)
    private ApplicationMaterialGenerationEntity generation;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
