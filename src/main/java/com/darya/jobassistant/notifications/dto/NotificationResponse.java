package com.darya.jobassistant.notifications.dto;

import com.darya.jobassistant.notifications.entity.NotificationStatus;
import com.darya.jobassistant.notifications.entity.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID telegramUserId,
        UUID applicationId,
        UUID interviewId,
        NotificationType type,
        String message,
        NotificationStatus status,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {
}
