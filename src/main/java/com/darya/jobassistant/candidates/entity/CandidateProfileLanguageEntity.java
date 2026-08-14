package com.darya.jobassistant.candidates.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * <p>{@link #languageCode} is stored lowercase and letters-only (see {@code
 * chk_candidate_profile_language_code_format} in V16); normalizing arbitrary input into that form
 * is explicit application-code work for a later step, not this entity's job.
 *
 * <p>{@link #proficiency} is a plain nullable string, not an enum: the project has no
 * language-proficiency model anywhere yet (the current YAML/domain {@code
 * CandidateProfile.languages} is a plain {@code List<String>}), so introducing one here would be
 * inventing a concept this step is not scoped to define.
 */
@Entity
@Table(name = "candidate_profile_language")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CandidateProfileLanguageEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfileEntity candidateProfile;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(length = 50)
    private String proficiency;

    /** Sprint 11 Step 5 (V28): deterministic CV presentation order - see {@code CandidateLanguage}'s javadoc. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
