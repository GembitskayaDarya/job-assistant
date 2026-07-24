package com.darya.jobassistant.vacancyimport.repository;

import java.time.Instant;
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
}
