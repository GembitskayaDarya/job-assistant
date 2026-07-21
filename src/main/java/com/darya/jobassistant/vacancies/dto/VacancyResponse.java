package com.darya.jobassistant.vacancies.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VacancyResponse(
        UUID id,
        UUID companyId,
        String companyName,
        String title,
        String description,
        String url,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String source,
        LocalDate postedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
