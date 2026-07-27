package com.darya.jobassistant.integrations.notifier;

import com.darya.jobassistant.ai.model.JobAnalysis;
import java.util.UUID;

/**
 * A vacancy match ready to be sent to a Telegram recipient. Carries the analysis as one
 * {@link JobAnalysis} domain value rather than duplicating its fields, so the monitoring
 * notification flow renders from the exact same contract the manual {@code /analyze} flow does -
 * see {@code JobAnalysisTelegramFormatter}, which both {@code JobMessageFormatter} and {@code
 * TelegramJobNotificationFormatter} delegate to for the shared analysis block.
 */
public record JobNotification(
        UUID vacancyId,
        Long recipientChatId,
        String title,
        String companyName,
        String url,
        JobAnalysis analysis
) {
    public JobNotification {
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
        if (recipientChatId == null || recipientChatId == 0L) {
            throw new IllegalArgumentException("recipientChatId must be a valid Telegram chat ID");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (analysis == null) {
            throw new IllegalArgumentException("analysis must not be null");
        }
    }
}
