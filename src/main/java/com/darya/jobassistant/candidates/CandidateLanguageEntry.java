package com.darya.jobassistant.candidates;

/**
 * Acceptance correction: one language entry in the YAML-oriented {@link CandidateProfile} - the
 * import-facing counterpart of {@code candidates.aggregate.CandidateLanguage}, mirroring how
 * {@link CandidateEducationEntry} relates to {@code candidates.aggregate.CandidateEducation}.
 * Replaces the previous plain {@code List<String>} representation, which had no way to express
 * {@code proficiency} at all.
 *
 * <p>{@link #language} is required; {@link #proficiency} stays nullable at this general-purpose
 * type's own level, preserving {@code candidates.aggregate.CandidateLanguage}'s existing domain
 * invariant (a language's proficiency may legitimately be unknown) - this type is also how {@code
 * CandidateProfileAnalysisAssembler} round-trips already-persisted facts, where a nullable
 * proficiency is a real, valid case, not just a YAML-authoring gap. The private-YAML <em>import
 * boundary</em> specifically is stricter: {@code YamlCandidateProfileMigrationSource} requires
 * both fields non-blank for every configured language entry, rejecting an incomplete one with a
 * clear error rather than silently importing a half-specified fact - see that class. {@link
 * #proficiency} stays a plain string, not an enum - see {@code
 * candidates.aggregate.CandidateLanguage}'s javadoc for why no such enum exists in this project.
 * No {@code displayOrder} field: {@code CandidateProfileYamlImportMapper} assigns it from this
 * entry's position in {@link CandidateProfile#languages()}, the same convention already used for
 * education and {@code preferredContractTypes}.
 */
public record CandidateLanguageEntry(
        String language,
        String proficiency
) {
    public CandidateLanguageEntry {
        language = trimToNull(language);
        if (language == null) {
            throw new IllegalArgumentException("Candidate language entry language must not be null or blank");
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
