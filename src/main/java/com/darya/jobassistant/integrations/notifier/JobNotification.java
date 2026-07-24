package com.darya.jobassistant.integrations.notifier;

import java.util.List;
import java.util.UUID;

public record JobNotification(
        UUID vacancyId,
        Long recipientChatId,
        String title,
        String companyName,
        String url,
        int matchScore,
        String summary,
        List<String> pros,
        List<String> cons,
        List<String> missingSkills
) {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

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
        if (matchScore < MIN_SCORE || matchScore > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "matchScore must be between %d and %d, but was %d".formatted(MIN_SCORE, MAX_SCORE, matchScore));
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        pros = requireCleanList(pros, "pros");
        cons = requireCleanList(cons, "cons");
        missingSkills = requireCleanList(missingSkills, "missingSkills");
    }

    private static List<String> requireCleanList(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null elements");
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not contain blank elements");
            }
        }
        return List.copyOf(values);
    }
}
