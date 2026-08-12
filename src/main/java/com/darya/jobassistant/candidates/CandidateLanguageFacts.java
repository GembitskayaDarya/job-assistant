package com.darya.jobassistant.candidates;

/**
 * Sprint 11 Step 1 correction: one language within {@link CandidateProfileFacts} - the complete,
 * lossless counterpart of the vacancy-analysis-oriented {@code CandidateProfile.languages()} (which
 * drops proficiency entirely and keeps only the display name). {@link #name} is the resolved
 * display name (e.g. {@code "English"}), not the raw ISO code the persisted {@code
 * candidates.aggregate.CandidateLanguage} carries - a code is a persistence/domain-internal
 * representation, never something legitimately displayed on a CV. {@link #proficiency} is preserved
 * exactly as the source domain has it: a nullable free-text value, never inferred or normalized -
 * see {@code candidates.aggregate.CandidateLanguage}'s own javadoc for why no proficiency enum
 * exists for languages in this project.
 */
public record CandidateLanguageFacts(String name, String proficiency) {

    public CandidateLanguageFacts {
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Candidate language fact name must not be null or blank");
        }
        proficiency = trimToNull(proficiency);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
