package com.darya.jobassistant.candidates.repository;

import com.darya.jobassistant.candidates.entity.CandidateProfilePreferenceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CandidateProfilePreferenceEntity} - Sprint 9 Step 3
 * persistence foundation, needed because preferences are persisted directly (this entity has no
 * owning collection on {@code CandidateProfileEntity}, matching {@code CandidateProfileSkillEntity}
 * and {@code CandidateProfileLanguageEntity}). Not yet called by any application service outside
 * {@code candidates.persistence}.
 */
public interface CandidateProfilePreferenceRepository extends JpaRepository<CandidateProfilePreferenceEntity, UUID> {

    /** Unordered - only used where row order is irrelevant (e.g. deleting the full existing set before a replace). */
    List<CandidateProfilePreferenceEntity> findByCandidateProfileId(UUID candidateProfileId);

    /**
     * Deliberately ordered rather than {@code findByCandidateProfileId} - PostgreSQL row order is
     * never guaranteed absent an explicit {@code ORDER BY}, and {@code priorityOrder}'s business
     * meaning (see {@code CandidatePreferenceType#isOrderSignificant()}) must round-trip through
     * persistence exactly, not merely by accident of physical storage order. {@code priorityOrder}
     * ascending puts an order-significant type's rows back in source-list order; {@code id}
     * ascending is only a deterministic tiebreaker for the many rows (every non-order-significant
     * type) where {@code priorityOrder} is {@code null} and therefore does not distinguish them.
     */
    List<CandidateProfilePreferenceEntity> findByCandidateProfileIdOrderByPriorityOrderAscIdAsc(UUID candidateProfileId);
}
