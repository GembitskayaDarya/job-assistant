package com.darya.jobassistant.integrations.ai.openai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire shape of the extraction AI response, deserialized directly by {@code ChatClient}'s
 * structured-output support. Deliberately package-private and distinct from {@code
 * ExtractedVacancyData}: {@code remotePolicy} and {@code postedDate} are kept as raw strings here
 * so {@link SpringAiVacancyExtractionAdapter} controls their parsing explicitly (and can reject an
 * unsupported/malformed value with a clear, sanitized message) rather than leaving it to Jackson's
 * default enum/date deserialization behavior. Never crosses the {@code VacancyExtractionPort}
 * boundary.
 */
record VacancyExtractionResponseDto(
        String title,
        String company,
        String location,
        String remotePolicy,
        List<String> contractTypes,
        List<String> requiredSkills,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String salaryText,
        String postedDate
) {
}
