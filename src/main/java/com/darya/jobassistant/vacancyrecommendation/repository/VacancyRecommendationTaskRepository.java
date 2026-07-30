package com.darya.jobassistant.vacancyrecommendation.repository;

import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationFailureCategory;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskOutcome;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskStatus;
import com.darya.jobassistant.vacancyrecommendation.entity.VacancyRecommendationTaskEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Application-facing persistence contract for {@link VacancyRecommendationTaskEntity}. Callers
 * should only rely on the domain-typed methods here - {@link #createPending}, {@link
 * #selectClaimCandidates}/{@link #claimByIds} (the two-statement claim, see {@link
 * com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationProcessingService} for how
 * they are used together inside one short transaction), {@link #completeTask}, {@link
 * #scheduleRetry}, and {@link #markDead} - never a Spring Data exception type.
 *
 * <p>Every "database current time" reference in the native claim queries below uses {@code (now()
 * AT TIME ZONE 'utc')} rather than a bound Java {@code Instant} parameter or plain {@code now()}:
 * this project's JPA session is configured with {@code hibernate.jdbc.time_zone=UTC} (see {@code
 * application.yml}), so this expression is exactly what Hibernate itself would write for {@code
 * Instant.now()} into these same {@code TIMESTAMP WITHOUT TIME ZONE} columns - using plain {@code
 * now()} instead would silently compare against the database session's own time zone, which is a
 * real, previously-hit bug class in this project (see {@code JobDiscoverySchedulerConcurrencyTest}
 * from Sprint 8 Step 9 for the exact failure mode). Using database time (not application-clock
 * time) also means claim eligibility is correct even if this JVM's clock has drifted.
 */
public interface VacancyRecommendationTaskRepository extends JpaRepository<VacancyRecommendationTaskEntity, UUID> {

    Optional<VacancyRecommendationTaskEntity> findByVacancyId(UUID vacancyId);

    /**
     * Inserts exactly one {@code PENDING} task for a brand-new {@code Vacancy} id - always called
     * from within the same {@code REQUIRES_NEW} transaction that just inserted that {@code
     * Vacancy} (see {@code VacancyIngestionService#persistDiscovered}), so a plain insert (no
     * conditional upsert) is correct: a freshly generated {@code Vacancy} id can never already
     * have a task. Any failure here (including violating {@code
     * uk_vacancy_recommendation_task_vacancy}, which should be structurally unreachable on this
     * path) propagates and rolls back that same transaction - the {@code Vacancy} is never
     * committed without its task.
     */
    default void createPending(UUID vacancyId, Instant now) {
        save(VacancyRecommendationTaskEntity.builder()
                .vacancyId(vacancyId)
                .status(VacancyRecommendationTaskStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .build());
    }

    /**
     * First half of the claim: locks up to {@code batchSize} eligible rows (deterministically
     * ordered by {@code next_attempt_at, created_at, id}) with {@code FOR UPDATE SKIP LOCKED}, so a
     * concurrent claimer on another connection/node skips straight past these rows to its own
     * candidates rather than blocking. Returns the pre-claim entities (their {@code status} is
     * still whatever it was before claiming) so the caller can tell a lease-recovered {@code
     * PROCESSING} row apart from a fresh {@code PENDING}/{@code RETRY_WAIT} one.
     *
     * <p>Must run in the same transaction as {@link #claimByIds} - the row locks {@code FOR UPDATE}
     * takes are held only for the duration of that transaction, which is exactly what prevents
     * another claimer from claiming the same rows between this call and the following {@code
     * UPDATE}.
     */
    @Query(value = """
            SELECT * FROM vacancy_recommendation_task
            WHERE (status = 'PENDING')
               OR (status = 'RETRY_WAIT' AND next_attempt_at <= (now() AT TIME ZONE 'utc'))
               OR (status = 'PROCESSING' AND lease_until < (now() AT TIME ZONE 'utc'))
            ORDER BY next_attempt_at ASC, created_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<VacancyRecommendationTaskEntity> selectClaimCandidates(@Param("batchSize") int batchSize);

    /**
     * Second half of the claim: transitions exactly the rows {@link #selectClaimCandidates} just
     * locked to {@code PROCESSING}, incrementing {@code attempt_count} exactly once and setting a
     * fresh {@code lease_until}/{@code lease_owner}/{@code updated_at} - all computed from database
     * time, never a bound Java {@code Instant}.
     */
    @Query(value = """
            UPDATE vacancy_recommendation_task
            SET status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                lease_until = (now() AT TIME ZONE 'utc') + (:leaseSeconds || ' seconds')::interval,
                lease_owner = :owner,
                updated_at = (now() AT TIME ZONE 'utc')
            WHERE id IN (:ids)
            RETURNING *
            """, nativeQuery = true)
    List<VacancyRecommendationTaskEntity> claimByIds(
            @Param("ids") List<UUID> ids, @Param("leaseSeconds") long leaseSeconds, @Param("owner") String owner);

    /**
     * Atomically completes a task this caller currently owns the lease for - covers every {@code
     * COMPLETED} outcome ({@code NOTIFIED}, {@code BELOW_SCORE_THRESHOLD}, {@code
     * MANUALLY_REVIEWED}, {@code ALREADY_NOTIFIED}, {@code ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC}).
     * The predicate checks both {@code status = PROCESSING} and {@code lease_owner = owner}, so a
     * worker whose lease has since expired and been reclaimed by another node can never clobber
     * that node's progress.
     *
     * @return true if this call performed the transition, false if the row's lease was no longer
     *      owned by this caller (a concurrency/invariant event the caller must not treat as success)
     */
    default boolean completeTask(UUID id, String owner, VacancyRecommendationTaskOutcome outcome, Instant completedAt) {
        return completeIfLeaseOwned(id, owner, outcome, completedAt,
                VacancyRecommendationTaskStatus.COMPLETED, VacancyRecommendationTaskStatus.PROCESSING) == 1;
    }

    @Modifying
    @Query("""
            UPDATE VacancyRecommendationTaskEntity t
            SET t.status = :completedStatus, t.outcome = :outcome, t.completedAt = :completedAt,
                t.leaseUntil = NULL, t.leaseOwner = NULL, t.updatedAt = :completedAt
            WHERE t.id = :id AND t.status = :processingStatus AND t.leaseOwner = :owner
            """)
    int completeIfLeaseOwned(
            @Param("id") UUID id, @Param("owner") String owner, @Param("outcome") VacancyRecommendationTaskOutcome outcome,
            @Param("completedAt") Instant completedAt,
            @Param("completedStatus") VacancyRecommendationTaskStatus completedStatus,
            @Param("processingStatus") VacancyRecommendationTaskStatus processingStatus);

    /**
     * Atomically moves a recoverably-failed task back to {@code RETRY_WAIT}, clearing the lease so
     * a future claim (by this or any other node) is free to pick it up once {@code nextAttemptAt}
     * passes. Same lease-ownership guard as {@link #completeTask}.
     *
     * @return true if this call performed the transition, false if the lease was no longer owned
     */
    default boolean scheduleRetry(UUID id, String owner, Instant nextAttemptAt,
                                   VacancyRecommendationFailureCategory category, Instant now) {
        return scheduleRetryIfLeaseOwned(id, owner, nextAttemptAt, category, now,
                VacancyRecommendationTaskStatus.RETRY_WAIT, VacancyRecommendationTaskStatus.PROCESSING) == 1;
    }

    @Modifying
    @Query("""
            UPDATE VacancyRecommendationTaskEntity t
            SET t.status = :retryStatus, t.nextAttemptAt = :nextAttemptAt, t.lastErrorCategory = :category,
                t.leaseUntil = NULL, t.leaseOwner = NULL, t.updatedAt = :now
            WHERE t.id = :id AND t.status = :processingStatus AND t.leaseOwner = :owner
            """)
    int scheduleRetryIfLeaseOwned(
            @Param("id") UUID id, @Param("owner") String owner, @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("category") VacancyRecommendationFailureCategory category, @Param("now") Instant now,
            @Param("retryStatus") VacancyRecommendationTaskStatus retryStatus,
            @Param("processingStatus") VacancyRecommendationTaskStatus processingStatus);

    /**
     * Atomically terminates a task as {@code DEAD}/{@code PERMANENT_FAILURE} - {@code maxAttempts}
     * exhausted, or a non-recoverable failure. Same lease-ownership guard as {@link #completeTask}.
     *
     * @return true if this call performed the transition, false if the lease was no longer owned
     */
    default boolean markDead(UUID id, String owner, VacancyRecommendationFailureCategory category, Instant completedAt) {
        return markDeadIfLeaseOwned(id, owner, category, completedAt,
                VacancyRecommendationTaskStatus.DEAD, VacancyRecommendationTaskOutcome.PERMANENT_FAILURE,
                VacancyRecommendationTaskStatus.PROCESSING) == 1;
    }

    @Modifying
    @Query("""
            UPDATE VacancyRecommendationTaskEntity t
            SET t.status = :deadStatus, t.outcome = :permanentFailure, t.lastErrorCategory = :category,
                t.completedAt = :completedAt, t.leaseUntil = NULL, t.leaseOwner = NULL, t.updatedAt = :completedAt
            WHERE t.id = :id AND t.status = :processingStatus AND t.leaseOwner = :owner
            """)
    int markDeadIfLeaseOwned(
            @Param("id") UUID id, @Param("owner") String owner, @Param("category") VacancyRecommendationFailureCategory category,
            @Param("completedAt") Instant completedAt,
            @Param("deadStatus") VacancyRecommendationTaskStatus deadStatus,
            @Param("permanentFailure") VacancyRecommendationTaskOutcome permanentFailure,
            @Param("processingStatus") VacancyRecommendationTaskStatus processingStatus);
}
