package com.darya.jobassistant.candidates;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 11 Step 5: one education entry within {@link CandidateProfileFacts} - the lossless
 * counterpart of {@code candidates.aggregate.CandidateEducation}, carried through unchanged (no
 * narrowing exists for education anywhere, unlike skills/languages). Fixed factual content -
 * never AI-generated or AI-tailored, and every entry always belongs on a CV (not a
 * selection/tailoring surface the way skills or personal projects are).
 *
 * <p>{@link #candidateEducationId} is propagated unchanged from {@code CandidateEducation#id()} -
 * {@code null} for a not-yet-persisted entry.
 */
public record CandidateEducationFacts(
        UUID candidateEducationId,
        String institution,
        String degree,
        String fieldOfStudy,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        int displayOrder
) {

    public CandidateEducationFacts {
        institution = institution == null ? null : institution.trim();
        if (institution == null || institution.isEmpty()) {
            throw new IllegalArgumentException("Candidate education fact institution must not be null or blank");
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
