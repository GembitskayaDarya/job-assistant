package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.UUID;

/**
 * One legacy {@code Vacancy} whose {@code sourceUrl} canonicalized to a value that is unique
 * among legacy rows and unused by any currently-populated {@code canonical_url} - the exact
 * {@code canonical_url} value {@code VacancyCanonicalUrlBackfillService} would write for this row.
 * Package-private: an internal planning result, never returned from a public API.
 */
record SafeCanonicalUrlAssignment(UUID vacancyId, String canonicalUrl) {
}
