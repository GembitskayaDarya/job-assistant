package com.darya.jobassistant.personalprojects.repository;

import com.darya.jobassistant.personalprojects.entity.PersonalProjectEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Internal Spring Data repository for {@link PersonalProjectEntity} - Sprint 11 Step 5. {@code
 * personalprojects.persistence.PersonalProjectRepositoryAdapter} is the only caller.
 */
public interface PersonalProjectRepository extends JpaRepository<PersonalProjectEntity, UUID> {

    /** Deterministic order: {@code display_order} ascending, {@code id} ascending as a tiebreaker - see {@code PersonalProjectRepositoryPort}. */
    List<PersonalProjectEntity> findAllByCandidateProfileIdOrderByDisplayOrderAscIdAsc(UUID candidateProfileId);

    /**
     * Deliberate, explicit optimistic-locking write for one project row only - always
     * unconditionally increments {@code version} and always checks {@code expectedVersion},
     * regardless of whether any scalar field actually changed, matching {@code
     * CandidateProfileRepository#updateIfVersionMatches}/{@code
     * CareerHistoryRepository#updateVersionIfMatches}'s convention: a save that only replaces this
     * project's highlights/technologies must still be treated as a real modification of this row.
     * Runs before {@code PersonalProjectRepositoryAdapter} touches any highlight/technology row -
     * a stale version must fail here, before any destructive delete of that project's existing
     * children.
     *
     * @return 1 if a row with this id and exactly this version existed and was updated, 0
     *     otherwise
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE PersonalProjectEntity p
            SET p.name = :name,
                p.description = :description,
                p.url = :url,
                p.startDate = :startDate,
                p.endDate = :endDate,
                p.displayOrder = :displayOrder,
                p.updatedAt = :updatedAt,
                p.version = p.version + 1
            WHERE p.id = :id AND p.version = :expectedVersion
            """)
    int updateVersionIfMatches(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("url") String url,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("displayOrder") int displayOrder,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);
}
