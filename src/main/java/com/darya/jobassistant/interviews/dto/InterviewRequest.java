package com.darya.jobassistant.interviews.dto;

import com.darya.jobassistant.interviews.entity.InterviewStatus;
import com.darya.jobassistant.interviews.entity.InterviewType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record InterviewRequest(
        @NotNull UUID applicationId,
        @NotNull Instant scheduledAt,
        @NotNull InterviewType type,
        @NotNull InterviewStatus status,
        String notes
) {
}
