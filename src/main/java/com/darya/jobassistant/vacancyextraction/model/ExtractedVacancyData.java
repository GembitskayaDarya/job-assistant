package com.darya.jobassistant.vacancyextraction.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Provider-independent structured facts extracted from a raw vacancy description or page. Never
 * carries candidate-matching information ({@code JobAnalysis} owns that). For guided manual
 * import, the authoritative vacancy description stays the raw pasted text on {@code
 * VacancyImportSession} - see {@code VacancyImportReviewService#buildCreationCommand} - never this
 * type; {@link #salaryMin}, {@link #salaryMax}, {@link #currency}, and {@link #postedDate} exist
 * because {@code VacancyCreationCommand}/{@code Vacancy} already have equivalent columns (today
 * only populated by RemoteOK ingestion), and a future automatic-discovery caller of {@code
 * VacancyExtractionService} needs this contract to be able to populate them too - guided import
 * itself still hardcodes them to {@code null} when building its own {@code
 * VacancyCreationCommand}, unchanged from before this type gained these fields.
 *
 * <p>The compact constructor only normalizes (trims strings, drops blank optional strings,
 * defaults a missing {@link RemotePolicy}, de-duplicates list entries while preserving order, and
 * guarantees non-null immutable collections). It never rejects data - required-field presence,
 * size/length bounds, and salary/currency relationships are a separate, explicit validation step
 * ({@code ExtractedVacancyDataValidator}), so a partially-populated AI response can still be
 * constructed and inspected before a decision is made about whether it is acceptable.
 */
public record ExtractedVacancyData(
        String title,
        String company,
        String location,
        RemotePolicy remotePolicy,
        List<String> contractTypes,
        List<String> requiredSkills,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String salaryText,
        LocalDate postedDate
) {
    public ExtractedVacancyData {
        title = trim(title);
        company = trim(company);
        location = trimToNull(location);
        remotePolicy = remotePolicy == null ? RemotePolicy.UNSPECIFIED : remotePolicy;
        contractTypes = normalizeList(contractTypes);
        requiredSkills = normalizeList(requiredSkills);
        currency = trimToNull(currency);
        salaryText = trimToNull(salaryText);
    }

    /**
     * Pre-Sprint-8-Step-6 shape, kept so every existing caller that never populated salary
     * amounts, currency, or a posted date keeps compiling unchanged.
     */
    public ExtractedVacancyData(String title, String company, String location, RemotePolicy remotePolicy,
                                 List<String> contractTypes, List<String> requiredSkills, String salaryText) {
        this(title, company, location, remotePolicy, contractTypes, requiredSkills, null, null, null, salaryText, null);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                deduplicated.add(trimmed);
            }
        }
        return List.copyOf(deduplicated);
    }
}
