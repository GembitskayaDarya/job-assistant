package com.darya.jobassistant.vacancyextraction.model;

import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.util.StringUtils;

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
    private static final int MAX_CURRENCY_LENGTH = 10;
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
        requireMaxLength(data.currency(), "currency", MAX_CURRENCY_LENGTH);
        requireBoundedList(data.contractTypes(), "contract types");
        requireBoundedList(data.requiredSkills(), "required skills");
        requirePositiveSalary(data.salaryMin(), "salary minimum");
        requirePositiveSalary(data.salaryMax(), "salary maximum");
        requireSalaryRangeOrdered(data);
        requireCurrencyOnlyWithSalaryInformation(data);
        return data;
    }

    private static void requirePositiveSalary(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new VacancyExtractionException("AI provider returned a non-positive " + field);
        }
    }

    private static void requireSalaryRangeOrdered(ExtractedVacancyData data) {
        if (data.salaryMin() != null && data.salaryMax() != null && data.salaryMin().compareTo(data.salaryMax()) > 0) {
            throw new VacancyExtractionException("AI provider returned a salary minimum greater than the salary maximum");
        }
    }

    /**
     * A currency is only meaningful alongside some other salary signal - a bare currency with no
     * amount and no salary phrase would otherwise let the AI assert "this role pays in USD"
     * without ever saying what it pays, which is exactly the kind of unstated-fact invention the
     * extraction prompt is told never to do.
     */
    private static void requireCurrencyOnlyWithSalaryInformation(ExtractedVacancyData data) {
        boolean hasSalaryInformation = data.salaryMin() != null || data.salaryMax() != null
                || StringUtils.hasText(data.salaryText());
        if (!hasSalaryInformation && data.currency() != null) {
            throw new VacancyExtractionException("AI provider returned a currency without any salary information");
        }
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
