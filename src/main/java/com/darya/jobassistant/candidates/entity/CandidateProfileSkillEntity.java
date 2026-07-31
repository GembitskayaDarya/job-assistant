package com.darya.jobassistant.candidates.entity;

import com.darya.jobassistant.candidates.SkillProficiency;
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
 * Sprint 9 Step 1 persistence foundation (V16) - see {@link CandidateProfileEntity}'s javadoc for
 * the relationship/cascade rationale shared by this entity.
 *
 * <p>{@link #proficiency} reuses the existing framework-free domain enum {@code
 * com.darya.jobassistant.candidates.SkillProficiency} rather than a duplicate persistence-only
 * enum: this is infrastructure depending on the domain package, the same valid direction the rest
 * of this project's port/adapter boundary already relies on, not the reverse. It has no {@code
 * NONE} level by design - a skill the candidate does not possess is represented by the absence of
 * a row here (see {@code chk_candidate_profile_skill_proficiency} in V16), never a nullable or
 * "none" value.
 */
@Entity
@Table(name = "candidate_profile_skill")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CandidateProfileSkillEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfileEntity candidateProfile;

    @Column(name = "skill_name", nullable = false, length = 255)
    private String skillName;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillProficiency proficiency;
}
