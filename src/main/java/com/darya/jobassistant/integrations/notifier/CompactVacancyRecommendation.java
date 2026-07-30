package com.darya.jobassistant.integrations.notifier;

import java.util.List;
import java.util.UUID;

/**
 * A vacancy match ready to be sent to a Telegram recipient as exactly one bounded message - the
 * automatic {@code VacancyRecommendationProcessingService} workflow's counterpart to {@link
 * JobNotification} (which the legacy {@code JobMonitoringService} workflow still uses unchanged,
 * complete with its own multi-message chunking).
 *
 * <p>Deliberately does not carry a {@code JobAnalysis} value or any Telegram type: {@code
 * reason}/{@code strengths}/{@code risks} are already the specific, bounded fields {@link
 * com.darya.jobassistant.integrations.notifier.telegram.CompactRecommendationTelegramFormatter}
 * needs, not a general-purpose analysis projection. Callers decide what "concise" means when
 * building one of these (see {@code JobNotificationFactory#createCompactRecommendation}) - this
 * record only validates that the essential identity fields are present and never blank.
 *
 * <p>{@code location}, {@code remoteMode}, and {@code salaryText} are nullable/blank-permitted:
 * "when present" fields the formatter renders only if given.
 */
public record CompactVacancyRecommendation(
        UUID vacancyId,
        Long recipientChatId,
        String title,
        String companyName,
        String url,
        int score,
        String reason,
        List<String> strengths,
        List<String> risks,
        String location,
        String remoteMode,
        String salaryText
) {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public CompactVacancyRecommendation {
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
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        risks = risks == null ? List.of() : List.copyOf(risks);
    }
}
