package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

/**
 * Thrown by {@link VacancyCanonicalUrlBackfillService#apply()} when something that should be
 * impossible given a freshly recomputed, blocker-free plan happens anyway while writing it:
 *
 * <ul>
 *   <li>a conditional {@code UPDATE ... WHERE id = :id AND canonical_url IS NULL} affects zero
 *       rows (the row was concurrently modified or deleted between planning and writing, inside
 *       the same transaction's own snapshot - should not happen under {@code REPEATABLE_READ}
 *       but is checked explicitly rather than assumed);
 *   <li>{@code uk_vacancy_canonical_url} is unexpectedly violated (a raw {@link
 *       org.springframework.dao.DataIntegrityViolationException} is caught and translated into
 *       this type - never left to propagate as a JPA/database exception type);
 *   <li>the total updated-row count does not match the planned assignment count.
 * </ul>
 *
 * <p>Always thrown from inside the APPLY transaction callback, so it rolls back every update this
 * run made, not just the one that triggered it - see {@link VacancyCanonicalUrlBackfillService}'s
 * javadoc for the full atomicity contract. The message deliberately never includes a source or
 * canonical URL.
 */
public class VacancyCanonicalUrlBackfillInvariantViolationException extends RuntimeException {

    public VacancyCanonicalUrlBackfillInvariantViolationException(String message) {
        super(message);
    }

    public VacancyCanonicalUrlBackfillInvariantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
