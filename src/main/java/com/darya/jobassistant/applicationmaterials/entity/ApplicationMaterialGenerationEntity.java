package com.darya.jobassistant.applicationmaterials.entity;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 10 Step 1 persistence foundation for one {@link ApplicationMaterialGenerationStatus}-
 * tracked application-material generation attempt (V21) - a Vacancy may own any number of these
 * rows, unlike {@code JobAnalysisEntity}'s one-per-vacancy shape. Referenced via a unidirectional
 * {@code @ManyToOne} (no owning collection on {@link Vacancy}), matching every other
 * vacancy-owned child entity in this codebase (e.g. {@code JobAnalysisEntity}, {@code
 * VacancyRecommendationTaskEntity}) - database {@code ON DELETE CASCADE} (V21) is solely
 * responsible for removing these rows when a vacancy is deleted, never JPA-level cascade.
 *
 * <p>{@link #version} is this row's optimistic-locking token. Matching {@code
 * CandidateProfileEntity}/{@code CareerHistoryEntity}'s convention, the actual application write
 * path always goes through {@code ApplicationMaterialGenerationRepository#updateIfVersionMatches}
 * (an explicit, unconditional version-checked JPQL update), not Hibernate's own dirty-checking
 * save - {@code @Version} here documents the column and backs Hibernate's schema validation, but
 * is not itself the enforcement mechanism for application saves.
 */
@Entity
@Table(name = "application_material_generation")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApplicationMaterialGenerationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacancy_id", nullable = false)
    private Vacancy vacancy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationMaterialGenerationStatus status;

    @Column(name = "candidate_profile_version", nullable = false)
    private long candidateProfileVersion;

    @Column(name = "career_history_version")
    private Long careerHistoryVersion;

    /**
     * Sprint 11 production hardening: {@code null} for a generation created before this field
     * existed ("legacy" - see {@link com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration}'s
     * own javadoc); every newly requested generation always carries a real 64-hex-char SHA-256
     * digest. Column stays nullable at the DB level (V31) - deliberately never backfilled for old
     * rows, which would falsely claim a document was produced from source state it never saw.
     */
    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Version
    @Column(nullable = false)
    private long version;
}
