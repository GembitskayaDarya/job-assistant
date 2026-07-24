package com.darya.jobassistant.monitoring.dto;

public record JobMonitoringCommand(
        String keyword,
        int minScore,
        int maxNotifications,
        Long recipientChatId
) {
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public JobMonitoringCommand {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        if (minScore < MIN_SCORE || minScore > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "minScore must be between %d and %d, but was %d".formatted(MIN_SCORE, MAX_SCORE, minScore));
        }
        if (maxNotifications <= 0) {
            throw new IllegalArgumentException("maxNotifications must be greater than zero");
        }
        if (recipientChatId == null || recipientChatId == 0L) {
            throw new IllegalArgumentException("recipientChatId must be a valid Telegram chat ID");
        }
    }
}
