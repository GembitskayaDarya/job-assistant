package com.darya.jobassistant.vacancies.dto;

import com.darya.jobassistant.vacancies.entity.Vacancy;

/**
 * Result of {@code VacancyCreationService#createIfAbsent}: unlike {@link VacancyPersistenceResult},
 * this always carries the resolved {@link Vacancy} - newly inserted or the pre-existing one under
 * the same canonical identity - since every current caller needs that vacancy's identity or state
 * either way.
 */
public record VacancyCreationResult(Vacancy vacancy, boolean newlyCreated) {

    public VacancyCreationResult {
        if (vacancy == null) {
            throw new IllegalArgumentException("vacancy must not be null");
        }
    }
}
