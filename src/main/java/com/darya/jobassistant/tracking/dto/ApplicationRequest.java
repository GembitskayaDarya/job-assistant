package com.darya.jobassistant.tracking.dto;

import com.darya.jobassistant.tracking.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationRequest(
        @NotNull UUID companyId,
        UUID vacancyId,
        Long telegramChatId,
        @NotNull ApplicationStatus status,
        @NotNull LocalDate appliedDate,
        String notes
) {
}
