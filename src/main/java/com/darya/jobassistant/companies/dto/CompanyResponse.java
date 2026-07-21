package com.darya.jobassistant.companies.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String website,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
