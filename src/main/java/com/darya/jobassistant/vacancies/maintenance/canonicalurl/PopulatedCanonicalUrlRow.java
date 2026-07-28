package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.UUID;

/**
 * One already-populated {@code canonical_url} value and the {@code Vacancy} it belongs to,
 * projected down to exactly those two columns. {@link VacancyCanonicalUrlAuditService} loads all
 * of these once per audit run to build the "currently used canonical identities" lookup a legacy
 * row's candidate is checked against - see that class's javadoc for the memory trade-off of
 * holding this set for the whole run.
 */
public record PopulatedCanonicalUrlRow(String canonicalUrl, UUID vacancyId) {
}
