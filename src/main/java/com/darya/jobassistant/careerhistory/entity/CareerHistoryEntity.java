package com.darya.jobassistant.careerhistory.entity;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 9 Step 5 persistence foundation for the Career History root (V19) - a separate aggregate
 * from {@link CandidateProfileEntity}, associated with it through a one-to-one, optional-on-the-
 * profile-side relationship: a Candidate Profile may have zero or one Career History, but a
 * Career History cannot exist without a Candidate Profile.
 *
 * <p>Mapped {@code @OneToOne} (not {@code @ManyToOne}, unlike every child entity below it in the
 * ownership chain) because that is this relationship's real cardinality - {@code
 * uk_career_history_candidate_profile_id} enforces it at the database level. Still unidirectional,
 * matching the rest of this codebase's parent/child convention: {@link CandidateProfileEntity} is
 * not modified to add a back-reference, so Candidate Profile's existing JPA model and Sprint 9
 * Step 4 runtime code are entirely unaffected by this step.
 *
 * <p>The complete child graph ({@link CareerCompanyEntity} and everything below it) is reached
 * only by querying on {@code career_history_id}/{@code career_company_id}/etc., never through a
 * {@code @OneToMany} collection here - same reasoning as {@code CandidateProfileEntity}'s
 * skills/languages/preferences: no owning collection, no cascade/orphan-removal at the JPA level,
 * database {@code ON DELETE CASCADE} (V19) is solely responsible for removing descendants.
 *
 * <p>{@link #version} is this step's one real optimistic-locking token - {@code @Version} is
 * deliberately not placed on any child entity. A child-only update (e.g. adding one responsibility)
 * does not yet increment this root version; making the whole aggregate's version increment on any
 * child change is {@code CareerHistoryRepositoryAdapter}'s job, following {@code
 * CandidateProfileRepositoryAdapter}'s pattern, and is Step 6 work - see {@code
 * CareerHistoryOptimisticLockingTest}'s javadoc for what is and is not proven at this step.
 */
@Entity
@Table(name = "career_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerHistoryEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false, unique = true)
    private CandidateProfileEntity candidateProfile;

    @Version
    @Column(nullable = false)
    private long version;
}
