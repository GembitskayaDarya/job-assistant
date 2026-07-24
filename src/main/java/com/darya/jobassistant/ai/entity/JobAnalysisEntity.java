package com.darya.jobassistant.ai.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted AI analysis result for a vacancy. {@code vacancy_id} is unique - at most one
 * analysis exists per vacancy - so this row is always the authoritative analysis for its
 * vacancy; there is no separate "pick the latest" rule to apply on read.
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

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> pros;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> cons;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "missing_skills", columnDefinition = "text[]")
    private List<String> missingSkills;
}
