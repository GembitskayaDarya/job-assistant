package com.darya.jobassistant.careerhistory.aggregate;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 9 Step 6: the one application/domain-facing persistence port for the complete Career
 * History aggregate (root plus its full company/position/project/bullet/technology graph - see
 * {@link CareerHistoryAggregate}) backed by V19/V20's PostgreSQL schema. Returns and accepts
 * {@link CareerHistoryAggregate} only - never a JPA entity, never a Spring Data type, never
 * {@code Pageable}.
 *
 * <p>Every table under Career History is treated as part of this one aggregate, not a separate
 * resource - there is deliberately no {@code CareerCompanyRepositoryPort}/{@code
 * CareerPositionRepositoryPort}/{@code CareerProjectRepositoryPort} or any bullet/technology port,
 * matching {@code CandidateProfileRepositoryPort}'s "skills and languages are parts of the
 * profile, not separate ports" convention.
 *
 * <p>Not yet wired into any application service or runtime workflow - {@code
 * PersistentCandidateProfileProvider}/{@code JobAnalysisService} are unaffected by this step, and
 * Career History remains entirely optional.
 */
public interface CareerHistoryRepositoryPort {

    /**
     * Loads the complete aggregate - root, every company, and their full descendant graph - as
     * one consistent snapshot, or {@link Optional#empty()} if no Career History exists for {@code
     * candidateProfileId}.
     */
    Optional<CareerHistoryAggregate> findByCandidateProfileId(UUID candidateProfileId);

    /**
     * Persists {@code careerHistory} as the complete desired state: creates a new row (and its
     * full supplied graph) when {@link CareerHistoryAggregate#id()} is {@code null}, otherwise
     * performs a version-checked update of the root and replaces the entire child graph with the
     * one supplied, even when only one nested child actually changed.
     *
     * @return the persisted aggregate, with durable ids throughout and the current (post-save)
     *     root version
     * @throws CareerHistoryConcurrentModificationException if {@code careerHistory} carries a
     *     version that no longer matches the row's current version - another transaction modified
     *     it first. The existing graph is left untouched by a failed call.
     * @throws CareerHistoryAlreadyExistsException if creating a new Career History (a {@code null}
     *     id) for a {@code candidateProfileId} that already has one
     * @throws CareerHistoryCandidateProfileNotFoundException if creating a new Career History for
     *     a {@code candidateProfileId} with no matching Candidate Profile
     */
    CareerHistoryAggregate save(CareerHistoryAggregate careerHistory);
}
