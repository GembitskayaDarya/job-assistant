package com.darya.jobassistant.notifications.query;

import java.util.List;

/**
 * Backlog discovery for notification candidates: persisted, successfully analyzed vacancies that
 * meet a score threshold and have no existing {@code NotificationDelivery} record (PENDING, SENT,
 * or FAILED all count) for the given recipient. Candidates found here may come from the current
 * ingestion run, an earlier run, or a run before the last application restart - eligibility does
 * not depend on when the vacancy was persisted or analyzed.
 *
 * <p>This is discovery only. Finding a candidate here does not reserve it; the caller must still
 * perform a separate atomic reservation (e.g. a {@code NotificationDeliveryPort.reserve} call)
 * before sending, since concurrent callers may race to reserve the same candidate.
 */
public interface JobNotificationCandidateQueryPort {

    /**
     * @param recipientChatId must be non-null and non-zero
     * @param minimumScore    must be between 0 and 100 inclusive
     * @param limit           must be greater than zero; the query may return fewer candidates
     *                        than this bound, but never more
     */
    List<JobNotificationCandidate> findCandidates(Long recipientChatId, int minimumScore, int limit);
}
