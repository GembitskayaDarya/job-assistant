package com.darya.jobassistant.candidates.aggregate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 11 Step 5: one education entry within a {@link CandidateProfileAggregate} - Tier-1 flat
 * factual data (no nested children, unlike Career History/Personal Projects), backed by V27's
 * {@code candidate_profile_education}. Fixed factual content - never AI-generated or AI-tailored,
 * and every entry is always shown on a CV (unlike skills or Personal Projects, this is not a
 * selection/tailoring surface).
 *
 * <p>Only {@link #institution} is mandatory: the factual import source may legitimately provide
 * only university/faculty information with no formal degree title, and nothing here may invent
 * one to satisfy a stricter constraint - {@link #degree} and {@link #fieldOfStudy} both stay
 * optional.
 */
public record CandidateEducation(
        UUID id,
        String institution,
        String degree,
        String fieldOfStudy,
        String location,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        int displayOrder
) {

    public CandidateEducation {
        institution = trimToNull(institution);
        if (institution == null) {
            throw new IllegalArgumentException("Candidate education institution must not be null or blank");
        }
        degree = trimToNull(degree);
        fieldOfStudy = trimToNull(fieldOfStudy);
        location = trimToNull(location);
        description = trimToNull(description);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Candidate education display order must not be negative");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Candidate education end date must not precede start date");
        }
    }

    /** Convenience constructor for an education entry with no persisted identity yet. */
    public CandidateEducation(
            String institution, String degree, String fieldOfStudy, String location,
            LocalDate startDate, LocalDate endDate, String description, int displayOrder) {
        this(null, institution, degree, fieldOfStudy, location, startDate, endDate, description, displayOrder);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
