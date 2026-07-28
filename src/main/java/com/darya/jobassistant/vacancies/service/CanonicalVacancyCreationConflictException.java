package com.darya.jobassistant.vacancies.service;

/**
 * Thrown by {@link VacancyCreationService#createIfAbsent} when the {@code Vacancy} insert loses a
 * concurrent race on {@code uk_vacancy_canonical_url}. Since {@link VacancyCreationService} no
 * longer owns its own transaction (it participates in the caller's via {@code
 * Propagation.MANDATORY}), it cannot safely look up the winning row itself - the physical
 * PostgreSQL transaction is already aborted at that point, and any further statement on it would
 * fail too. This exception is deliberately allowed to propagate out of the whole call, so the
 * caller's transaction rolls back completely (including any {@code Company} it created); the
 * caller is then responsible for retrying the full use case in a fresh transaction, where the
 * winning row is simply found by the normal "already exists" path.
 */
public class CanonicalVacancyCreationConflictException extends RuntimeException {

    public CanonicalVacancyCreationConflictException(String message) {
        super(message);
    }

    public CanonicalVacancyCreationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
