package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.List;
import java.util.UUID;

/**
 * Two or more legacy {@code Vacancy} rows that canonicalize to the same {@code canonicalUrl}.
 * {@code vacancyIds} always has size &gt;= 2 and is sorted for deterministic reporting. Package-
 * private: an internal planning result, never returned from a public API.
 */
record LegacyToLegacyCollisionGroup(String canonicalUrl, List<UUID> vacancyIds) {

    LegacyToLegacyCollisionGroup {
        vacancyIds = List.copyOf(vacancyIds);
    }
}
