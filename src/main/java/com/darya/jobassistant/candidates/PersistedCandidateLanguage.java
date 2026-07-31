package com.darya.jobassistant.candidates;

import java.util.regex.Pattern;

/**
 * A single language as persisted in {@code candidate_profile_language} (Sprint 9 Step 1/2).
 * {@code proficiency} stays a plain optional string rather than an enum: no language-proficiency
 * model exists anywhere in this project yet (the YAML/{@code CandidateProfile.languages} shape is
 * a plain {@code List<String>}), so inventing one here would define a concept this step is not
 * scoped to own. A future step may introduce a real enum once one is actually agreed on.
 */
public record PersistedCandidateLanguage(
        String languageCode,
        String proficiency
) {
    /**
     * Mirrors V16's {@code chk_candidate_profile_language_code_format} exactly: lowercase Latin
     * letters only, 2-3 characters. Invalid values are rejected here, not silently normalized -
     * the same deliberate choice Step 1 made at the database.
     */
    private static final Pattern LANGUAGE_CODE_FORMAT = Pattern.compile("^[a-z]{2,3}$");

    public PersistedCandidateLanguage {
        if (languageCode == null || !LANGUAGE_CODE_FORMAT.matcher(languageCode).matches()) {
            throw new IllegalArgumentException(
                    "Candidate profile language code must be 2-3 lowercase Latin letters, but was: " + languageCode);
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
