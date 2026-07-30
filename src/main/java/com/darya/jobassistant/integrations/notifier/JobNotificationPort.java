package com.darya.jobassistant.integrations.notifier;

public interface JobNotificationPort {

    JobNotificationResult send(JobNotification notification);

    /**
     * Sends a {@link CompactVacancyRecommendation} as exactly one Telegram message - never the
     * generic multi-chunk delivery {@link #send(JobNotification)} may use. An implementation must
     * fail with {@link JobNotificationFailureType#PAYLOAD_TOO_LARGE} - and make zero provider HTTP
     * calls - rather than ever splitting the recommendation across more than one message.
     */
    JobNotificationResult sendCompactRecommendation(CompactVacancyRecommendation recommendation);
}
