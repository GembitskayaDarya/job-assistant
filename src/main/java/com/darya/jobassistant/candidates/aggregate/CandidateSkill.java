package com.darya.jobassistant.candidates.aggregate;

import com.darya.jobassistant.candidates.SkillProficiency;

/**
 * A single skill within a {@link CandidateProfileAggregate}. Named {@code CandidateSkill} here
 * (in {@code candidates.aggregate}, not the top-level {@code candidates} package) deliberately
 * collides in simple name with the existing YAML-oriented {@code candidates.CandidateSkill}: the
 * two represent the same real-world concept from two different, not-yet-reconciled sources - see
 * {@link CandidateProfileAggregate}'s javadoc for why they are not merged yet. This type has no
 * {@code note} field (Step 1's schema has none) and an additional optional {@code category}
 * (Step 1's schema has one); the YAML type has the reverse.
 */
public record CandidateSkill(
        String name,
        String category,
        SkillProficiency proficiency
) {
    public CandidateSkill {
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
