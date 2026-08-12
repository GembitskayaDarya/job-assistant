package com.darya.jobassistant.candidates.aggregate;

import com.darya.jobassistant.candidates.SkillProficiency;
import java.util.UUID;

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
 *
 * <p>{@link #id} (Sprint 11 Step 2) is the skill row's own persisted {@code
 * candidate_profile_skill.id} - {@code null} for a not-yet-persisted skill, non-null once loaded
 * from {@link com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter}.
 * Column already existed (inherited from {@code BaseEntity}); this only stops the mapper from
 * dropping it. Like Career History's ids, it is stable for the lifetime of one loaded aggregate,
 * not durable across a subsequent profile save - {@code
 * CandidateProfileRepositoryAdapter#replaceSkills} deletes and reinserts every skill row on every
 * save, exactly like {@code CareerHistoryRepositoryAdapter} does for its own children. The
 * four-argument constructor is kept for the many existing call sites that construct a skill
 * in-memory with no persisted identity yet.
 */
public record CandidateSkill(
        UUID id,
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

    /** Convenience constructor for a skill with no persisted identity yet - see {@link #id}. */
    public CandidateSkill(String name, String category, String note, SkillProficiency proficiency) {
        this(null, name, category, note, proficiency);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
