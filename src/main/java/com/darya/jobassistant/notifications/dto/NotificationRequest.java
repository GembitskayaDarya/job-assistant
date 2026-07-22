package com.darya.jobassistant.notifications.dto;

import com.darya.jobassistant.notifications.entity.NotificationStatus;
import com.darya.jobassistant.notifications.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record NotificationRequest(
        @NotNull UUID userId,
        UUID applicationId,
        UUID interviewId,
        @NotNull NotificationType type,
        @NotBlank String message,
        NotificationStatus status,
        Instant sentAt
) {
}
