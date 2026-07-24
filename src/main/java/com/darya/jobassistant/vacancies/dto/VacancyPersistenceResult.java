package com.darya.jobassistant.vacancies.dto;

import com.darya.jobassistant.vacancies.entity.Vacancy;

public record VacancyPersistenceResult(Status status, Vacancy vacancy) {

    public enum Status {
        INSERTED,
        ALREADY_EXISTS
    }

    public VacancyPersistenceResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.INSERTED && vacancy == null) {
            throw new IllegalArgumentException("an INSERTED result must expose the persisted vacancy");
        }
        if (status == Status.ALREADY_EXISTS && vacancy != null) {
            throw new IllegalArgumentException("an ALREADY_EXISTS result must not expose a vacancy");
        }
    }

    public static VacancyPersistenceResult inserted(Vacancy vacancy) {
        if (vacancy == null) {
            throw new IllegalArgumentException("vacancy must not be null");
        }
        return new VacancyPersistenceResult(Status.INSERTED, vacancy);
    }

    public static VacancyPersistenceResult alreadyExists() {
        return new VacancyPersistenceResult(Status.ALREADY_EXISTS, null);
    }

    public boolean isInserted() {
        return status == Status.INSERTED;
    }
}
