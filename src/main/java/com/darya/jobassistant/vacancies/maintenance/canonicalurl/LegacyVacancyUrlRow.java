package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.UUID;

/**
 * One {@code vacancy} row with {@code canonical_url IS NULL}, projected down to exactly the two
 * columns {@link VacancyCanonicalUrlAuditService} needs to classify it. Deliberately not the full
 * {@code Vacancy} entity: the audit never reads {@code company}, {@code description}, salary,
 * analysis, or application/notification data, and loading those for every legacy row would be
 * pure waste for a scan that only ever inspects the source URL.
 */
public record LegacyVacancyUrlRow(UUID vacancyId, String sourceUrl) {
}
