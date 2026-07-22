package com.darya.jobassistant.integrations.jobsource;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JobPosting(
        String companyName,
        String title,
        String description,
        String url,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        LocalDate postedAt
) {
}