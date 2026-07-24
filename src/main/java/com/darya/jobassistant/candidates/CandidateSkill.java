package com.darya.jobassistant.candidates;

/**
 * A single skill in the candidate's profile, with how confidently they can use it. {@code note}
 * is free-text and optional (e.g. "used heavily at previous role") - it never encodes a separate
 * experience type, that distinction is deliberately not part of this model.
 */
public record CandidateSkill(
        String name,
        SkillProficiency proficiency,
        String note
) {
    public CandidateSkill {
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Candidate skill name must not be null or blank");
        }
        if (proficiency == null) {
            throw new IllegalArgumentException("Candidate skill proficiency must not be null");
        }
        note = trimToNull(note);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
