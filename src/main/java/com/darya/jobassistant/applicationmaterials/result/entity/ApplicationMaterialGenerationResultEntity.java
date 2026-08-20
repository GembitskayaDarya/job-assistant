package com.darya.jobassistant.applicationmaterials.result.entity;

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
 * Sprint 10 Step 3 persistence foundation for one {@link
 * com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult}
 * (V22). Does not extend the shared {@code BaseEntity}: results are write-once (see that
 * aggregate's javadoc) and have no {@code updated_at} column at all - {@code BaseEntity} would
 * require one. {@link #createdAt} is set explicitly by {@code
 * ApplicationMaterialGenerationResultRepositoryAdapter}, mirroring {@code
 * VacancyImportDraftEntity}'s same reasoning for not extending {@code BaseEntity}.
 *
 * <p>Referenced via a unidirectional {@code @ManyToOne} (no owning collection on {@link
 * ApplicationMaterialGenerationEntity}), matching every other parent/child pair in this codebase -
 * database {@code ON DELETE CASCADE} (V22) is solely responsible for removing this row when its
 * generation is deleted.
 *
 * <p>{@link #cvContent}/{@link #coverLetterContent} are genuine PostgreSQL {@code jsonb} columns:
 * {@code @JdbcTypeCode(SqlTypes.JSON)} on a plain {@link String} field tells Hibernate to write/read
 * that string as opaque JSON text via the driver's JSON type support, rather than reflecting over
 * the Java type to build a schema itself. The actual (de)serialization between this string and the
 * framework-free {@code TailoredCvDocument}/{@code GeneratedCoverLetter} domain types is owned entirely by
 * {@code ApplicationMaterialGenerationResultContentMapper} - this entity never sees those domain
 * types directly.
 */
@Entity
@Table(name = "application_material_generation_result")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApplicationMaterialGenerationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_id", nullable = false, unique = true)
    private ApplicationMaterialGenerationEntity generation;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cv_content", columnDefinition = "jsonb", nullable = false)
    private String cvContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cover_letter_content", columnDefinition = "jsonb", nullable = false)
    private String coverLetterContent;

    @Column(name = "ai_provider", nullable = false, length = 50)
    private String aiProvider;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "prompt_version", nullable = false)
    private int promptVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
