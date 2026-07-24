package com.darya.jobassistant.vacancyextraction.model;

import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import java.util.List;

/**
 * Application-owned validation of AI-extracted vacancy data. Successful JSON deserialization only
 * proves the AI provider returned syntactically valid output shaped like {@link
 * ExtractedVacancyData} - it says nothing about whether that content is usable. This validator is
 * the single place that decides "usable enough to persist as a draft", entirely in project code
 * with no further AI involvement.
 */
public final class ExtractedVacancyDataValidator {

    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_COMPANY_LENGTH = 300;
    private static final int MAX_LOCATION_LENGTH = 300;
    private static final int MAX_SALARY_TEXT_LENGTH = 200;
    private static final int MAX_LIST_SIZE = 30;
    private static final int MAX_LIST_ENTRY_LENGTH = 100;

    private ExtractedVacancyDataValidator() {
    }

    public static ExtractedVacancyData validate(ExtractedVacancyData data) {
        if (data == null) {
            throw new VacancyExtractionException("AI provider returned no extracted vacancy data");
        }
        requireNonBlank(data.title(), "title");
        requireMaxLength(data.title(), "title", MAX_TITLE_LENGTH);
        requireNonBlank(data.company(), "company");
        requireMaxLength(data.company(), "company", MAX_COMPANY_LENGTH);
        requireMaxLength(data.location(), "location", MAX_LOCATION_LENGTH);
        requireMaxLength(data.salaryText(), "salary text", MAX_SALARY_TEXT_LENGTH);
        requireBoundedList(data.contractTypes(), "contract types");
        requireBoundedList(data.requiredSkills(), "required skills");
        return data;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new VacancyExtractionException("AI provider returned a blank " + field);
        }
    }

    private static void requireMaxLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new VacancyExtractionException(
                    "AI provider returned a " + field + " exceeding " + maxLength + " characters");
        }
    }

    private static void requireBoundedList(List<String> values, String field) {
        if (values.size() > MAX_LIST_SIZE) {
            throw new VacancyExtractionException(
                    "AI provider returned too many " + field + " (" + values.size() + ")");
        }
        for (String value : values) {
            if (value.length() > MAX_LIST_ENTRY_LENGTH) {
                throw new VacancyExtractionException(
                        "AI provider returned a " + field + " entry exceeding " + MAX_LIST_ENTRY_LENGTH + " characters");
            }
        }
    }
}
