package com.darya.jobassistant.ai.repository;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Application-facing JobAnalysis persistence contract. Callers should only rely on the
 * domain-typed methods here ({@link #persist}, {@link #findCompletedByVacancyId}, {@link
 * #claimIfAbsent}, {@link #completeClaim}, {@link #reclaimStaleClaim}, {@link #releaseClaim}),
 * never the JPA entity or a Spring Data exception type. The {@code JpaRepository} superinterface
 * is an implementation detail that lets this interface stay the single port + adapter, per this
 * project's repository convention (see {@code NotificationDeliveryRepository}).
 *
 * <p>The claim/complete/release trio is this repository's concurrency mechanism for {@code
 * AnalyzeVacancyUseCase}'s on-demand, potentially concurrent callers - see {@link
 * #claimIfAbsent} for why it needs no separate table or distributed lock.
 */
public interface JobAnalysisRepository extends JpaRepository<JobAnalysisEntity, UUID> {

    Optional<JobAnalysisEntity> findByVacancyId(UUID vacancyId);

    List<JobAnalysisEntity> findByVacancyIdIn(Collection<UUID> vacancyIds);

    long deleteByVacancyIdAndStatus(UUID vacancyId, AnalysisStatus status);

    /**
     * Persists the authoritative analysis for a vacancy, bypassing the claim workflow entirely.
     * Used only by the automatic monitoring pipeline, which calls this exclusively for a vacancy
     * it just confirmed has no analysis yet (a brand-new ingestion) - a plain insert is always
     * valid there. The claim/complete/release methods below exist for {@code
     * AnalyzeVacancyUseCase}'s concurrent, on-demand callers instead, where "does an analysis
     * already exist" can never be assumed true.
     */
    default PersistedJobAnalysis persist(UUID vacancyId, JobAnalysis analysis) {
        JobAnalysisEntity saved = save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .status(AnalysisStatus.COMPLETED)
                .score(analysis.score())
                .summary(analysis.summary())
                .pros(analysis.pros())
                .cons(analysis.cons())
                .missingRequiredSkills(analysis.missingRequiredSkills())
                .missingPreferredSkills(analysis.missingPreferredSkills())
                .experienceAssessment(analysis.experienceAssessment())
                .preferencesAssessment(analysis.preferencesAssessment())
                .build());
        return toDomain(saved);
    }

    /** The persisted analysis for a vacancy, or empty if none exists yet or one is still in progress. */
    default Optional<PersistedJobAnalysis> findCompletedByVacancyId(UUID vacancyId) {
        return findByVacancyId(vacancyId)
                .filter(entity -> entity.getStatus() == AnalysisStatus.COMPLETED)
                .map(JobAnalysisRepository::toDomain);
    }

    /**
     * Atomically inserts an {@code IN_PROGRESS} claim for a vacancy unless one already exists,
     * using the unique constraint on {@code vacancy_id} - the same one that guarantees "at most
     * one analysis per vacancy" - as the sole source of truth for novelty. Avoids an
     * exists()-then-insert() check-then-act race across concurrent callers, exactly like {@code
     * VacancyRepository#saveIfAbsent} and {@code NotificationDeliveryRepository#reserve}: no
     * separate claim table, no distributed lock, just the same database-enforced uniqueness this
     * project already relies on elsewhere.
     */
    @Query(value = """
            INSERT INTO job_analysis
                (vacancy_id, status, pros, cons, missing_required_skills, missing_preferred_skills, created_at, updated_at)
            VALUES (:vacancyId, 'IN_PROGRESS', '{}', '{}', '{}', '{}', :claimedAt, :claimedAt)
            ON CONFLICT (vacancy_id) DO NOTHING
            RETURNING *
            """, nativeQuery = true)
    Optional<JobAnalysisEntity> claimIfAbsent(@Param("vacancyId") UUID vacancyId, @Param("claimedAt") Instant claimedAt);

    /**
     * Atomically transitions a claim from {@code IN_PROGRESS} to {@code COMPLETED}, storing the
     * result. Backed by a JPQL (not native) bulk update: the {@code text[]}-mapped list fields
     * need Hibernate's entity-attribute type metadata to bind correctly, which only JPQL resolves
     * for a bulk statement - a raw native query would need a lower-level, Hibernate-version-
     * specific array-parameter binding this project has no other precedent for.
     *
     * @return true if this call performed the transition, false if the row was no longer
     *      {@code IN_PROGRESS} by the time this statement ran
     */
    default boolean completeClaim(UUID vacancyId, JobAnalysis analysis, Instant completedAt) {
        int updatedRows = completeClaimIfInProgress(
                vacancyId,
                analysis.score(),
                analysis.summary(),
                analysis.pros(),
                analysis.cons(),
                analysis.missingRequiredSkills(),
                analysis.missingPreferredSkills(),
                analysis.experienceAssessment(),
                analysis.preferencesAssessment(),
                completedAt,
                AnalysisStatus.COMPLETED,
                AnalysisStatus.IN_PROGRESS);
        return updatedRows == 1;
    }

    @Modifying
    @Query("""
            UPDATE JobAnalysisEntity e
            SET e.status = :completedStatus, e.score = :score, e.summary = :summary,
                e.pros = :pros, e.cons = :cons,
                e.missingRequiredSkills = :missingRequiredSkills, e.missingPreferredSkills = :missingPreferredSkills,
                e.experienceAssessment = :experienceAssessment, e.preferencesAssessment = :preferencesAssessment,
                e.updatedAt = :completedAt
            WHERE e.vacancyId = :vacancyId AND e.status = :inProgressStatus
            """)
    int completeClaimIfInProgress(
            @Param("vacancyId") UUID vacancyId,
            @Param("score") int score,
            @Param("summary") String summary,
            @Param("pros") List<String> pros,
            @Param("cons") List<String> cons,
            @Param("missingRequiredSkills") List<String> missingRequiredSkills,
            @Param("missingPreferredSkills") List<String> missingPreferredSkills,
            @Param("experienceAssessment") String experienceAssessment,
            @Param("preferencesAssessment") String preferencesAssessment,
            @Param("completedAt") Instant completedAt,
            @Param("completedStatus") AnalysisStatus completedStatus,
            @Param("inProgressStatus") AnalysisStatus inProgressStatus);

    /**
     * Atomically bumps {@code updatedAt} on a claim that is still {@code IN_PROGRESS} but older
     * than {@code staleThreshold}, recovering it from an abandoned attempt (e.g. a crashed
     * request) so a new caller can retry the AI call instead of being blocked behind it
     * indefinitely.
     *
     * @return true if this call reclaimed the row
     */
    default boolean reclaimStaleClaim(UUID vacancyId, Instant now, Instant staleThreshold) {
        return reclaimStaleClaimIfAbandoned(vacancyId, now, staleThreshold, AnalysisStatus.IN_PROGRESS) == 1;
    }

    @Modifying
    @Query("""
            UPDATE JobAnalysisEntity e
            SET e.updatedAt = :now
            WHERE e.vacancyId = :vacancyId AND e.status = :inProgressStatus AND e.updatedAt < :staleThreshold
            """)
    int reclaimStaleClaimIfAbandoned(
            @Param("vacancyId") UUID vacancyId,
            @Param("now") Instant now,
            @Param("staleThreshold") Instant staleThreshold,
            @Param("inProgressStatus") AnalysisStatus inProgressStatus);

    /**
     * Releases (deletes) a claim that is still {@code IN_PROGRESS}, after the AI call or
     * validation failed. Deleting rather than recording a failure status means the unique
     * constraint on {@code vacancy_id} never blocks an immediate retry, and no partial result is
     * ever left behind.
     */
    default boolean releaseClaim(UUID vacancyId) {
        return deleteByVacancyIdAndStatus(vacancyId, AnalysisStatus.IN_PROGRESS) == 1;
    }

    static PersistedJobAnalysis toDomain(JobAnalysisEntity entity) {
        return new PersistedJobAnalysis(
                entity.getId(),
                entity.getVacancyId(),
                new JobAnalysis(
                        entity.getScore(),
                        entity.getPros(),
                        entity.getCons(),
                        entity.getMissingRequiredSkills(),
                        entity.getMissingPreferredSkills(),
                        entity.getExperienceAssessment(),
                        entity.getPreferencesAssessment(),
                        entity.getSummary()),
                entity.getCreatedAt());
    }
}
