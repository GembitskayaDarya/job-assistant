package com.darya.jobassistant.candidates.aggregate;

import java.util.Optional;

/**
 * Application/domain-facing persistence port for the complete Candidate Profile aggregate
 * (parent scalars, skills, and languages together - see {@link CandidateProfileAggregate}) backed
 * by Sprint 9 Step 1's PostgreSQL schema. Returns and accepts {@link CandidateProfileAggregate}
 * only - never a JPA entity, never a Spring Data type.
 *
 * <p>Skills and languages are treated as parts of the Candidate Profile aggregate, not separate
 * resources - there is deliberately no {@code CandidateSkillRepositoryPort} or {@code
 * CandidateLanguageRepositoryPort}.
 *
 * <p>This is a different abstraction from {@code CandidateProfileProvider}, and the two are not
 * merged in this step: this port is persistence access to the (still currently unused)
 * PostgreSQL-backed aggregate, while {@code CandidateProfileProvider} remains the actual source AI
 * vacancy analysis reads from today - the YAML-backed {@code
 * com.darya.jobassistant.candidates.CandidateProfile}.
 */
public interface CandidateProfileRepositoryPort {

    /**
     * Loads the complete aggregate - parent scalars, all skills, all languages - as one
     * consistent snapshot, or {@link Optional#empty()} if no profile has this key.
     */
    Optional<CandidateProfileAggregate> findByProfileKey(String profileKey);

    /**
     * Persists {@code profile} as the complete desired state: creates a new row when {@link
     * CandidateProfileAggregate#id()} is {@code null}, otherwise atomically performs a
     * version-checked update of the whole aggregate - parent scalars, skills, and languages
     * together - and increments {@link CandidateProfileAggregate#version()}, even when only
     * skills or languages changed and every parent scalar field is unchanged.
     *
     * @return the persisted aggregate, with its durable id and current (post-save) version
     * @throws CandidateProfileConcurrentModificationException if {@code profile} carries a
     *     version that no longer matches the row's current version - i.e. another transaction
     *     modified it first. Skills and languages are left untouched by a failed call.
     */
    CandidateProfileAggregate save(CandidateProfileAggregate profile);
}
