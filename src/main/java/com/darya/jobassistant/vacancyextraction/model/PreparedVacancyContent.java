package com.darya.jobassistant.vacancyextraction.model;

/**
 * The deterministic result of {@code VacancyExtractionContentPreparer#prepare}: the content
 * actually sent to the AI provider, plus counts kept only for internal observability (never
 * logged as the content itself - see {@code VacancyExtractionService}).
 */
public record PreparedVacancyContent(String content, int originalCharCount, int preparedCharCount, boolean truncated) {

    public PreparedVacancyContent {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
