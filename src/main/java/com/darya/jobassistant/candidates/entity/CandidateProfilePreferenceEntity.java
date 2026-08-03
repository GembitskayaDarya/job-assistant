package com.darya.jobassistant.candidates.entity;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 9 Step 3 persistence for one weighted/repeatable Candidate Profile preference (V17) -
 * see {@link CandidateProfileEntity}'s javadoc for the relationship/cascade rationale shared by
 * this entity.
 *
 * <p>{@link #type} and {@link #importance} both reuse existing framework-free domain enums
 * ({@code com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType} and {@code
 * com.darya.jobassistant.candidates.PreferenceImportance}) rather than persistence-only
 * duplicates, mapped {@code EnumType.STRING} - the same valid infra-depends-on-domain direction
 * {@link CandidateProfileSkillEntity#getProficiency()} already relies on.
 *
 * <p>{@link #priorityOrder} (V18) is non-null exactly when {@link #type}{@code
 * .isOrderSignificant()} is {@code true} - see {@code CandidateProfilePreference}'s javadoc.
 */
@Entity
@Table(name = "candidate_profile_preference")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CandidateProfilePreferenceEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfileEntity candidateProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false, length = 30)
    private CandidatePreferenceType type;

    @Column(name = "preference_value", nullable = false, length = 255)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PreferenceImportance importance;

    @Column(name = "priority_order")
    private Integer priorityOrder;
}
