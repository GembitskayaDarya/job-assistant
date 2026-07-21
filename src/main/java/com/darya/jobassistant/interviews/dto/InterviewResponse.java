package com.darya.jobassistant.interviews.dto;

import com.darya.jobassistant.interviews.entity.InterviewStatus;
import com.darya.jobassistant.interviews.entity.InterviewType;
import java.time.Instant;
import java.util.UUID;

public record InterviewResponse(
        UUID id,
        UUID applicationId,
        Instant scheduledAt,
        InterviewType type,
        InterviewStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
