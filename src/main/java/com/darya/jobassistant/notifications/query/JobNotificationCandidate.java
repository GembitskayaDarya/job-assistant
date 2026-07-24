package com.darya.jobassistant.notifications.query;

import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.vacancies.entity.Vacancy;

/**
 * A persisted, successfully analyzed vacancy that is eligible to be considered for notification:
 * it has a durable identity, an authoritative {@link PersistedJobAnalysis}, and (per
 * {@link JobNotificationCandidateQueryPort}) no existing delivery record for the recipient the
 * query was run for. Discovery only - turning a candidate into an actual send still requires a
 * separate atomic delivery reservation.
 */
public record JobNotificationCandidate(
        Vacancy vacancy,
        PersistedJobAnalysis analysis
) {
    public JobNotificationCandidate {
        if (vacancy == null) {
            throw new IllegalArgumentException("vacancy must not be null");
        }
        if (analysis == null) {
            throw new IllegalArgumentException("analysis must not be null");
        }
        if (vacancy.getId() == null) {
            throw new IllegalArgumentException("vacancy must have a durable id");
        }
        if (!analysis.vacancyId().equals(vacancy.getId())) {
            throw new IllegalArgumentException(
                    "analysis vacancyId " + analysis.vacancyId() + " does not match vacancy id " + vacancy.getId());
        }
    }
}
