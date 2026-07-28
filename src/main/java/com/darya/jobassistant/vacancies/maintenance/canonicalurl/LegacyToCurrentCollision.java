package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.UUID;

/**
 * One legacy {@code Vacancy} whose canonical candidate is already used by a different, already-
 * populated {@code Vacancy} ({@code currentOwnerVacancyId}). Package-private: an internal planning
 * result, never returned from a public API.
 */
record LegacyToCurrentCollision(UUID vacancyId, String canonicalUrl, UUID currentOwnerVacancyId) {
}
