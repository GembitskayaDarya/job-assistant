package com.darya.jobassistant.candidates;

/**
 * A single skill as persisted in {@code candidate_profile_skill} (Sprint 9 Step 1/2) - distinct
 * from {@link CandidateSkill} because the persisted shape has no {@code note} column and an
 * additional optional {@code category}. A skill the candidate does not possess is represented by
 * its absence from {@link PersistedCandidateProfile#skills()}, never by a "none" proficiency.
 */
public record PersistedCandidateSkill(
        String name,
        String category,
        SkillProficiency proficiency
) {
    public PersistedCandidateSkill {
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile skill name must not be null or blank");
        }
        if (proficiency == null) {
            throw new IllegalArgumentException("Candidate profile skill proficiency must not be null");
        }
        category = trimToNull(category);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
