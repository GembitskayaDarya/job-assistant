package com.darya.jobassistant.ai.entity;

import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted AI analysis for a vacancy - and, while {@code status} is {@link AnalysisStatus#IN_PROGRESS},
 * the concurrency claim for that vacancy's analysis. {@code vacancy_id} is unique regardless of
 * status, so the same constraint that guarantees "at most one analysis per vacancy" also
 * guarantees "at most one in-flight AI call per vacancy" - there is no separate claim table.
 * {@code score}/{@code summary} are null exactly while {@code status} is {@code IN_PROGRESS}, a
 * claim row carrying no result yet; they are always present once {@code status} is
 * {@code COMPLETED}. There is no {@code FAILED} status: a failed AI call releases (deletes) its
 * claim row entirely, so the unique constraint never blocks a retry.
 *
 * <p>{@code reanalysisTargetVersion}/{@code reanalysisClaimedAt} are a second, independent claim
 * mechanism for recalculating an already-{@code COMPLETED} row whose {@code analysisVersion} is
 * outdated: unlike the {@code IN_PROGRESS} claim above, the row stays {@code COMPLETED} and every
 * existing analysis field stays readable for the whole reanalysis attempt, so a concurrent reader
 * never sees a gap. Both fields are null exactly when no reanalysis is in flight.
 */
@Entity
@Table(name = "job_analysis")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class JobAnalysisEntity extends BaseEntity {

    @Column(name = "vacancy_id", nullable = false, unique = true)
    private UUID vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> pros;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> cons;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "missing_required_skills", columnDefinition = "text[]")
    private List<String> missingRequiredSkills;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "missing_preferred_skills", columnDefinition = "text[]")
    private List<String> missingPreferredSkills;

    @Column(name = "experience_assessment", columnDefinition = "TEXT")
    private String experienceAssessment;

    @Column(name = "preferences_assessment", columnDefinition = "TEXT")
    private String preferencesAssessment;

    @Column(name = "analysis_version", nullable = false)
    private int analysisVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_origin", nullable = false, length = 20)
    private AnalysisOrigin analysisOrigin;

    @Column(name = "reanalysis_target_version")
    private Integer reanalysisTargetVersion;

    @Column(name = "reanalysis_claimed_at")
    private Instant reanalysisClaimedAt;
}
