package com.darya.jobassistant.vacancies.dto;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.List;

public record VacancyIngestionResult(
        int fetchedCount,
        List<Vacancy> persistedVacancies,
        int alreadyKnownCount
) {
    private static final VacancyIngestionResult EMPTY = new VacancyIngestionResult(0, List.of(), 0);

    public VacancyIngestionResult {
        if (fetchedCount < 0) {
            throw new IllegalArgumentException("fetchedCount must not be negative");
        }
        if (alreadyKnownCount < 0) {
            throw new IllegalArgumentException("alreadyKnownCount must not be negative");
        }
        if (persistedVacancies == null) {
            throw new IllegalArgumentException("persistedVacancies must not be null");
        }
        for (Vacancy vacancy : persistedVacancies) {
            if (vacancy == null) {
                throw new IllegalArgumentException("persistedVacancies must not contain null elements");
            }
        }
        persistedVacancies = List.copyOf(persistedVacancies);
    }

    public static VacancyIngestionResult empty() {
        return EMPTY;
    }
}
