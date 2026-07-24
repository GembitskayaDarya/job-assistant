package com.darya.jobassistant.integrations.notifier;

import java.util.Optional;

public record JobNotificationResult(Optional<String> externalMessageId) {

    public JobNotificationResult {
        externalMessageId = externalMessageId == null ? Optional.empty() : externalMessageId;
        if (externalMessageId.isPresent() && externalMessageId.get().isBlank()) {
            throw new IllegalArgumentException("externalMessageId must not be blank when present");
        }
    }

    public static JobNotificationResult accepted() {
        return new JobNotificationResult(Optional.empty());
    }

    public static JobNotificationResult accepted(String externalMessageId) {
        if (externalMessageId == null || externalMessageId.isBlank()) {
            throw new IllegalArgumentException("externalMessageId must not be blank");
        }
        return new JobNotificationResult(Optional.of(externalMessageId));
    }
}
