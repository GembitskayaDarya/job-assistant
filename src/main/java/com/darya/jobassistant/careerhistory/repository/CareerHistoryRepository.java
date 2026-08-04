package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerHistoryEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Internal Spring Data repository for {@link CareerHistoryEntity} - Sprint 9 Step 5 persistence
 * foundation, extended by Step 6 with {@link #updateVersionIfMatches}. {@code
 * CareerHistoryRepositoryPort}/{@code CareerHistoryRepositoryAdapter} (Step 6) are the only
 * callers.
 */
public interface CareerHistoryRepository extends JpaRepository<CareerHistoryEntity, UUID> {

    Optional<CareerHistoryEntity> findByCandidateProfileId(UUID candidateProfileId);

    /**
     * Deliberate, explicit optimistic-locking write for the root row only - always unconditionally
     * increments {@code version} and always checks {@code expectedVersion} against whatever is
     * currently stored, matching {@code CandidateProfileRepository#updateIfVersionMatches}'s
     * convention exactly. Independent of Hibernate's own dirty checking: the Career History
     * aggregate's version must increment for a save even when every child changed and no root
     * scalar field did, and this update runs before any child row is touched (see {@code
     * CareerHistoryRepositoryAdapter#save}) - a stale version must fail here, before any
     * destructive delete of the existing child graph.
     *
     * <p>{@code updatedAt} is set explicitly because a JPQL bulk update bypasses the {@code
     * @LastModifiedDate}/{@code AuditingEntityListener} lifecycle callback entirely. {@code
     * clearAutomatically = true} for the same reason {@code CandidateProfileRepository}'s
     * equivalent method does: a bulk update never touches an already-managed entity in the current
     * persistence context, so any caller that re-reads this row afterward needs a fresh fetch, not
     * stale first-level-cache state.
     *
     * @return 1 if a row with this id, this candidate profile id, and exactly this version existed
     *     and was updated, 0 otherwise (the row does not exist, {@code candidateProfileId} does
     *     not match, or {@code expectedVersion} is stale) - the caller must translate 0 to {@code
     *     CareerHistoryConcurrentModificationException}, never a silent no-op success
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CareerHistoryEntity h
            SET h.updatedAt = :updatedAt,
                h.version = h.version + 1
            WHERE h.id = :id AND h.candidateProfile.id = :candidateProfileId AND h.version = :expectedVersion
            """)
    int updateVersionIfMatches(
            @Param("id") UUID id,
            @Param("candidateProfileId") UUID candidateProfileId,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);
}
