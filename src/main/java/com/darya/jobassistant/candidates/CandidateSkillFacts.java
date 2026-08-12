package com.darya.jobassistant.candidates;

import java.util.UUID;

/**
 * Sprint 11 Step 1 correction: one skill within {@link CandidateProfileFacts} - the complete,
 * lossless counterpart of the vacancy-analysis-oriented {@link CandidateSkill} (which drops {@link
 * #category}). A distinct type from {@code candidates.aggregate.CandidateSkill} despite the
 * identical shape: this one belongs to the framework-free, non-aggregate-coupled facts model that
 * {@link CandidateContextSnapshot} and downstream projections such as {@code CvSourceSnapshot} are
 * allowed to depend on, whereas the aggregate type is persistence-adjacent and must not leak past
 * the {@code candidates.migration}/{@code candidatecontext.runtime} boundary.
 *
 * <p>{@link #candidateSkillId} (Sprint 11 Step 2) is propagated unchanged from {@code
 * candidates.aggregate.CandidateSkill#id()} - see that type's javadoc for its stability semantics.
 * This is the identity a future CV tailoring result references to select/order skills without the
 * AI ever inventing a skill name; {@code null} for a not-yet-persisted skill.
 */
public record CandidateSkillFacts(UUID candidateSkillId, String name, String category, String note, SkillProficiency proficiency) {

    public CandidateSkillFacts {
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Candidate skill fact name must not be null or blank");
        }
        if (proficiency == null) {
            throw new IllegalArgumentException("Candidate skill fact proficiency must not be null");
        }
        category = trimToNull(category);
        note = trimToNull(note);
    }

    /** Convenience constructor for a skill with no persisted identity yet - see {@link #candidateSkillId}. */
    public CandidateSkillFacts(String name, String category, String note, SkillProficiency proficiency) {
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
