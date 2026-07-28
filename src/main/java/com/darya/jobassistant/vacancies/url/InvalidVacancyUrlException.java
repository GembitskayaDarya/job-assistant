package com.darya.jobassistant.vacancies.url;

/**
 * Thrown when a URL cannot be treated as a valid vacancy URL - either the raw input given to
 * {@link VacancyUrlCanonicalizer#canonicalize} or an already-canonical value being wrapped in a
 * {@link CanonicalVacancyUrl}. This is a pure input-validation failure, never an external
 * provider or network failure - see {@code JobDiscoveryException} (in {@code
 * integrations.jobsearch}) for that distinct concern.
 */
public class InvalidVacancyUrlException extends RuntimeException {

    public InvalidVacancyUrlException(String message) {
        super(message);
    }
}
