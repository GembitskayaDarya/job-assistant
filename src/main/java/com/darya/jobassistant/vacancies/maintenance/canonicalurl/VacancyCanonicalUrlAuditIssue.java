package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import java.util.List;
import java.util.UUID;

/**
 * One classified legacy {@code Vacancy} that is not safe to backfill, carrying just enough for
 * later maintenance work to act on - never the source URL, description, or any other vacancy
 * field. {@code canonicalCandidate} is null for {@link VacancyCanonicalUrlAuditIssueType#INVALID_SOURCE_URL}
 * (canonicalization never produced a value) and populated for the two collision types.
 * {@code relatedVacancyIds} is empty for {@code INVALID_SOURCE_URL}, the rest of the colliding
 * group (excluding this row) for {@code LEGACY_TO_LEGACY_COLLISION}, and the single already-current
 * {@code Vacancy} id that owns the conflicting {@code canonical_url} for {@code
 * LEGACY_TO_CURRENT_COLLISION}.
 *
 * <p>{@code canonicalCandidate} is intentionally never written to the application log by {@code
 * VacancyCanonicalUrlAuditRunner} - it may still contain non-tracking query parameters carried
 * over from the source URL, so only {@code vacancyId}, {@code issueType}, and {@code
 * relatedVacancyIds} (all UUIDs) are logged.
 */
public record VacancyCanonicalUrlAuditIssue(
        UUID vacancyId,
        VacancyCanonicalUrlAuditIssueType issueType,
        String canonicalCandidate,
        List<UUID> relatedVacancyIds) {

    public VacancyCanonicalUrlAuditIssue {
        relatedVacancyIds = relatedVacancyIds == null ? List.of() : List.copyOf(relatedVacancyIds);
    }
}
