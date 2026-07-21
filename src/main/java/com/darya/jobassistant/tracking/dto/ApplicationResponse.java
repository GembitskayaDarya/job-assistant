package com.darya.jobassistant.tracking.dto;

import com.darya.jobassistant.tracking.entity.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID companyId,
        String companyName,
        UUID vacancyId,
        String vacancyTitle,
        Long telegramChatId,
        ApplicationStatus status,
        LocalDate appliedDate,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
