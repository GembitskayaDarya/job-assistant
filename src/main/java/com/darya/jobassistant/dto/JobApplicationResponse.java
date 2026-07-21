package com.darya.jobassistant.dto;

import com.darya.jobassistant.entity.ApplicationStatus;
import java.time.Instant;
import java.time.LocalDate;

public record JobApplicationResponse(
        Long id,
        String company,
        String position,
        ApplicationStatus status,
        LocalDate appliedDate,
        Long telegramChatId,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}