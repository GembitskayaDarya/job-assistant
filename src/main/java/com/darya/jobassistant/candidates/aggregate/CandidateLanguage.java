package com.darya.jobassistant.candidates.aggregate;

import java.util.regex.Pattern;

/**
 * A single language within a {@link CandidateProfileAggregate}. {@code proficiency} stays a plain
 * optional string rather than an enum: no language-proficiency model exists anywhere in this
 * project yet (the YAML/{@code CandidateProfile.languages} shape is a plain {@code List<String>}),
 * so inventing one here would define a concept this step is not scoped to own. A future step may
 * introduce a real enum once one is actually agreed on.
 *
 * <p>{@link #displayOrder} (Sprint 11 Step 5) is the deterministic CV presentation order (V28's
 * {@code display_order}) - every other multi-entry list in this aggregate already had one; this
 * was the one gap. It is a real factual invariant, not a cosmetic default: there is deliberately
 * no convenience constructor that silently assigns {@code displayOrder = 0} - every caller must
 * state the order explicitly. Cross-list uniqueness (no two languages in one profile sharing a
 * {@code displayOrder}) is enforced by {@link CandidateProfileAggregate}'s {@code
 * requireUniqueLanguageDisplayOrders}, mirroring how it already enforces unique skill names and
 * language codes - the database's {@code uk_candidate_profile_language_profile_id_display_order}
 * remains defense-in-depth, not the primary place an invalid aggregate is caught.
 */
public record CandidateLanguage(
        String languageCode,
        String proficiency,
        int displayOrder
) {
    /**
     * Mirrors V16's {@code chk_candidate_profile_language_code_format} exactly: lowercase Latin
     * letters only, 2-3 characters. Invalid values are rejected here, not silently normalized -
     * the same deliberate choice Step 1 made at the database.
     */
    private static final Pattern LANGUAGE_CODE_FORMAT = Pattern.compile("^[a-z]{2,3}$");

    public CandidateLanguage {
        if (languageCode == null || !LANGUAGE_CODE_FORMAT.matcher(languageCode).matches()) {
            throw new IllegalArgumentException(
                    "Candidate profile language code must be 2-3 lowercase Latin letters, but was: " + languageCode);
        }
        // Deliberately unconstrained beyond "trim to null" - see class javadoc. The database
        // column (VARCHAR(50)) is the sole length authority; an overlong value is a legitimate
        // domain-valid-but-database-invalid case, exercised by
        // CandidateProfileRepositoryAdapterTest's public-port rollback test.
        proficiency = trimToNull(proficiency);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Candidate profile language display order must not be negative");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
