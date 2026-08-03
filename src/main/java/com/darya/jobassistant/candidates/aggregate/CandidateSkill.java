package com.darya.jobassistant.candidates.aggregate;

import com.darya.jobassistant.candidates.SkillProficiency;

/**
 * A single skill within a {@link CandidateProfileAggregate}. Named {@code CandidateSkill} here
 * (in {@code candidates.aggregate}, not the top-level {@code candidates} package) deliberately
 * collides in simple name with the existing YAML-oriented {@code candidates.CandidateSkill}: the
 * two represent the same real-world concept from two different, not-yet-reconciled sources - see
 * {@link CandidateProfileAggregate}'s javadoc for why they are not merged yet.
 *
 * <p>{@code note} (added Sprint 9 Step 3, backed by {@code candidate_profile_skill.note}) exists
 * purely so the YAML-to-aggregate migration never silently discards {@code
 * com.darya.jobassistant.candidates.CandidateSkill#note} - it is not read by AI vacancy analysis
 * ({@code JobAnalysisService.formatSkills} only ever reads {@code name}/{@code proficiency}).
 */
public record CandidateSkill(
        String name,
        String category,
        String note,
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
