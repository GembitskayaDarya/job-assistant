package com.darya.jobassistant.candidates;

import java.time.LocalDate;

/**
 * Sprint 11 Step 5: one education entry in the YAML-oriented {@link CandidateProfile} - the
 * import-facing counterpart of {@code candidates.aggregate.CandidateEducation}, mirroring how
 * {@link CandidateSkill} relates to {@code candidates.aggregate.CandidateSkill}. No {@code
 * displayOrder} field: {@code CandidateProfileYamlImportMapper} assigns it from this entry's
 * position in {@link CandidateProfile#education()} - the YAML list's own order already is the
 * intended presentation order, the same convention already used for {@code
 * preferredContractTypes}.
 *
 * <p>Only {@link #institution} is mandatory - the factual source may legitimately provide only
 * university/faculty information with no formal degree title.
 */
public record CandidateEducationEntry(
        String institution,
        String degree,
        String fieldOfStudy,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {
    public CandidateEducationEntry {
        institution = trimToNull(institution);
        if (institution == null) {
            throw new IllegalArgumentException("Candidate education entry institution must not be null or blank");
        }
        degree = trimToNull(degree);
        fieldOfStudy = trimToNull(fieldOfStudy);
        location = trimToNull(location);
        description = trimToNull(description);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
