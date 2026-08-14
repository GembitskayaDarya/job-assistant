package com.darya.jobassistant.candidates.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 11 Step 5 persistence for one education entry (V27) - Tier-1, flat, no nested children,
 * matching {@link CandidateProfileSkillEntity}/{@link CandidateProfileLanguageEntity}'s shape
 * exactly: unidirectional {@code @ManyToOne}, database {@code ON DELETE CASCADE} (never JPA-level
 * cascade), versioned together with the whole {@link CandidateProfileEntity} save.
 *
 * <p>{@link #degree}/{@link #fieldOfStudy} are both nullable - the factual import source may only
 * provide university/faculty information without a formal degree title, and nothing is entitled
 * to invent one.
 */
@Entity
@Table(name = "candidate_profile_education")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CandidateProfileEducationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfileEntity candidateProfile;

    @Column(nullable = false, length = 255)
    private String institution;

    @Column(length = 255)
    private String degree;

    @Column(name = "field_of_study", length = 255)
    private String fieldOfStudy;

    @Column(length = 300)
    private String location;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
