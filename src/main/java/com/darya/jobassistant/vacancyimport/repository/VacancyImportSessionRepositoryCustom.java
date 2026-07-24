package com.darya.jobassistant.vacancyimport.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VacancyImportSessionRepositoryCustom {

    /**
     * Atomically transitions a session from {@code WAITING_FOR_DESCRIPTION} to {@code EXTRACTING},
     * storing the description and bumping {@code updatedAt} in the same statement. The current
     * state is part of the update's own predicate (mirrors {@code
     * NotificationDeliveryRepositoryImpl}'s {@code markSent}/{@code markFailed}), so two
     * concurrent description submissions for the same session can never both apply - whichever
     * commits second updates zero rows and is reported back as a lost race, rather than silently
     * overwriting the winner's data.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code WAITING_FOR_DESCRIPTION} by the time this statement ran
     */
    boolean acceptDescriptionIfWaiting(UUID sessionId, String rawDescription, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code EXTRACTING} to {@code WAITING_FOR_CONFIRMATION}.
     * Used by {@code VacancyImportService.extract} together with the draft insert, in the same
     * database transaction, so a draft is never left behind by a session that failed to advance.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code EXTRACTING} by the time this statement ran
     */
    boolean moveToWaitingForConfirmationIfExtracting(UUID sessionId, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code EXTRACTING} to {@code FAILED}. Guarded the same
     * way as {@link #moveToWaitingForConfirmationIfExtracting}, so a failure detected after another
     * operation already resolved the session (successfully or otherwise) never overwrites that
     * outcome.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code EXTRACTING} by the time this statement ran
     */
    boolean moveToFailedIfExtracting(UUID sessionId, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code WAITING_FOR_CONFIRMATION} to {@code COMPLETED}
     * and records the resulting {@code vacancy_id} in the same statement. Called from inside the
     * same database transaction as the Vacancy creation/lookup, so if this returns {@code false}
     * the caller rolls the whole transaction back rather than leaving a Vacancy that no session
     * ended up linked to.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code WAITING_FOR_CONFIRMATION} by the time this statement ran
     */
    boolean completeIfWaitingForConfirmation(UUID sessionId, UUID vacancyId, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code WAITING_FOR_CONFIRMATION} back to
     * {@code WAITING_FOR_DESCRIPTION}, clearing the stale raw description in the same statement
     * (mirrors {@link VacancyImportSession#retryDescription}, which does the equivalent in-memory).
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code WAITING_FOR_CONFIRMATION} by the time this statement ran
     */
    boolean retryIfWaitingForConfirmation(UUID sessionId, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code WAITING_FOR_CONFIRMATION} to {@code CANCELLED}.
     * Guarded the same way as the other conditional transitions here, so a Cancel callback racing
     * a concurrent Save (or a second Cancel) can never silently clobber the winning outcome.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code WAITING_FOR_CONFIRMATION} by the time this statement ran
     */
    boolean cancelIfWaitingForConfirmation(UUID sessionId, Instant updatedAt);

    /**
     * Atomically transitions a session from {@code WAITING_FOR_CONFIRMATION} to {@code EXPIRED}.
     * Used by {@code VacancyImportReviewService} before acting on any callback, so an expired
     * confirmation session is never saved, retried, or cancelled as though it were still active.
     *
     * @return true if this call performed the transition, false if the session was no longer in
     *      {@code WAITING_FOR_CONFIRMATION} by the time this statement ran
     */
    boolean expireIfWaitingForConfirmation(UUID sessionId, Instant updatedAt);

    /**
     * IDs of active (non-terminal) sessions whose {@code expires_at} is at or before {@code
     * expiresBefore}, oldest expiration first, bounded to {@code limit} rows. Backs {@code
     * VacancyImportExpirationService}'s cleanup batches - id-only and bounded so one scheduled run
     * never loads an unbounded backlog into memory, unlike the per-callback checks above which
     * only ever look at one session already known by id.
     */
    List<UUID> findExpiredActiveSessionIds(Instant expiresBefore, int limit);

    /**
     * Atomically transitions a session from any active state ({@code WAITING_FOR_URL}, {@code
     * WAITING_FOR_DESCRIPTION}, {@code EXTRACTING}, or {@code WAITING_FOR_CONFIRMATION}) to
     * {@code EXPIRED}, but only if it is still active and its {@code expires_at} is still at or
     * before {@code expiresBefore}. The second condition matters because a session can legitimately
     * be re-armed with a later {@code expires_at} between being selected as a cleanup candidate and
     * this call running (not currently possible - sessions don't get their TTL extended today -
     * but the guard costs nothing and removes the assumption). A zero-row result here is a normal
     * skip: the user acted on the session (or another operation resolved it) between selection and
     * this call, and that outcome must never be overwritten.
     *
     * @return true if this call performed the transition
     */
    boolean expireIfActiveAndExpired(UUID sessionId, Instant expiresBefore, Instant updatedAt);
}
