package com.darya.jobassistant.vacancies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VacancyRequest(
        @NotNull UUID companyId,
        @NotBlank String title,
        String description,
        String url,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String source,
        LocalDate postedAt
) {
}
