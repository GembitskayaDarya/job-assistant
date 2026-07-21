package com.darya.jobassistant.dto;

import com.darya.jobassistant.entity.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record JobApplicationRequest(
        @NotBlank String company,
        @NotBlank String position,
        @NotNull ApplicationStatus status,
        @NotNull LocalDate appliedDate,
        Long telegramChatId,
        String notes
) {
}