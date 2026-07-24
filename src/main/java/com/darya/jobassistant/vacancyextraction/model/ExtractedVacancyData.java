package com.darya.jobassistant.vacancyextraction.model;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Provider-independent structured facts extracted from a raw vacancy description. Never carries
 * candidate-matching information ({@code JobAnalysis} owns that) and never replaces the raw
 * description itself, which stays on {@code VacancyImportSession}.
 *
 * <p>The compact constructor only normalizes (trims strings, drops blank optional strings,
 * defaults a missing {@link RemotePolicy}, de-duplicates list entries while preserving order, and
 * guarantees non-null immutable collections). It never rejects data - required-field presence and
 * size/length bounds are a separate, explicit validation step ({@code
 * ExtractedVacancyDataValidator}), so a partially-populated AI response can still be constructed
 * and inspected before a decision is made about whether it is acceptable.
 */
public record ExtractedVacancyData(
        String title,
        String company,
        String location,
        RemotePolicy remotePolicy,
        List<String> contractTypes,
        List<String> requiredSkills,
        String salaryText
) {
    public ExtractedVacancyData {
        title = trim(title);
        company = trim(company);
        location = trimToNull(location);
        remotePolicy = remotePolicy == null ? RemotePolicy.UNSPECIFIED : remotePolicy;
        contractTypes = normalizeList(contractTypes);
        requiredSkills = normalizeList(requiredSkills);
        salaryText = trimToNull(salaryText);
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
