package com.darya.jobassistant.applicationmaterials.repository;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Internal Spring Data repository for {@link ApplicationMaterialGenerationEntity} - Sprint 10
 * Step 1 persistence foundation. {@code ApplicationMaterialGenerationRepositoryPort}/{@code
 * ApplicationMaterialGenerationRepositoryAdapter} are the only intended callers.
 */
public interface ApplicationMaterialGenerationRepository extends JpaRepository<ApplicationMaterialGenerationEntity, UUID> {

    List<ApplicationMaterialGenerationEntity> findAllByVacancyIdOrderByRequestedAtDesc(UUID vacancyId);

    /**
     * Deliberate, explicit optimistic-locking write for the whole row - always unconditionally
     * increments {@code version} and always checks {@code expectedVersion} against whatever is
     * currently stored, matching {@code CandidateProfileRepository#updateIfVersionMatches}/{@code
     * CareerHistoryRepository#updateVersionIfMatches}'s convention exactly, rather than relying on
     * Hibernate's own {@code @Version} dirty-checking save.
     *
     * <p>{@code updatedAt} is set explicitly because a JPQL bulk update bypasses the {@code
     * @LastModifiedDate}/{@code AuditingEntityListener} lifecycle callback entirely.
     *
     * <p>{@code clearAutomatically = true} because a bulk update never touches any already-managed
     * entity in the current persistence context - the caller must re-fetch by id afterward to get
     * a fresh managed instance rather than stale first-level-cache state.
     *
     * @return 1 if a row with this id and exactly this version existed and was updated, 0
     *     otherwise (the row does not exist, or {@code expectedVersion} is stale) - the caller
     *     must translate 0 to a concurrency failure, never treat it as a silent no-op success
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ApplicationMaterialGenerationEntity g
            SET g.status = :status,
                g.candidateProfileVersion = :candidateProfileVersion,
                g.careerHistoryVersion = :careerHistoryVersion,
                g.startedAt = :startedAt,
                g.completedAt = :completedAt,
                g.failureCode = :failureCode,
                g.failureMessage = :failureMessage,
                g.updatedAt = :updatedAt,
                g.version = g.version + 1
            WHERE g.id = :id AND g.version = :expectedVersion
            """)
    int updateIfVersionMatches(
            @Param("id") UUID id,
            @Param("status") ApplicationMaterialGenerationStatus status,
            @Param("candidateProfileVersion") long candidateProfileVersion,
            @Param("careerHistoryVersion") Long careerHistoryVersion,
            @Param("startedAt") Instant startedAt,
            @Param("completedAt") Instant completedAt,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);
}
