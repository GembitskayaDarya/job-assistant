package com.darya.jobassistant.candidates.migration;

import java.util.Map;

/**
 * Deterministic, bounded lookup between the free-text display names {@code
 * CandidateProfile.languages} carries (e.g. {@code "English"}) and the lowercase ISO 639-1/639-2
 * codes {@code CandidateLanguage.languageCode} requires (see V16's {@code
 * chk_candidate_profile_language_code_format}). Shared by {@link CandidateProfileYamlImportMapper}
 * (name -&gt; code) and {@link CandidateProfileAnalysisAssembler} (code -&gt; name) so a round trip
 * always reproduces the original display name exactly.
 *
 * <p>Deliberately not a general-purpose locale/language library: this is a small, explicit,
 * reviewable table covering the languages a real candidate profile is realistically going to name.
 * A name that is not in this table is invalid source data - {@link #codeForName} throws rather
 * than guessing or silently dropping it, per Sprint 9 Step 3's "invalid source data fails
 * validation rather than being discarded" rule.
 */
final class CandidateProfileLanguageCodes {

    private static final Map<String, String> CODE_BY_LOWERCASE_NAME = Map.ofEntries(
            Map.entry("english", "en"),
            Map.entry("polish", "pl"),
            Map.entry("russian", "ru"),
            Map.entry("german", "de"),
            Map.entry("french", "fr"),
            Map.entry("spanish", "es"),
            Map.entry("italian", "it"),
            Map.entry("portuguese", "pt"),
            Map.entry("dutch", "nl"),
            Map.entry("ukrainian", "uk"),
            Map.entry("swedish", "sv"),
            Map.entry("norwegian", "no"),
            Map.entry("danish", "da"),
            Map.entry("finnish", "fi"),
            Map.entry("czech", "cs"),
            Map.entry("slovak", "sk"),
            Map.entry("hungarian", "hu"),
            Map.entry("romanian", "ro"),
            Map.entry("bulgarian", "bg"),
            Map.entry("greek", "el"),
            Map.entry("turkish", "tr"),
            Map.entry("arabic", "ar"),
            Map.entry("chinese", "zh"),
            Map.entry("japanese", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("hindi", "hi"));

    private static final Map<String, String> NAME_BY_CODE = CODE_BY_LOWERCASE_NAME.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getValue,
                    entry -> capitalize(entry.getKey())));

    private CandidateProfileLanguageCodes() {
    }

    /**
     * @throws IllegalArgumentException if {@code displayName} is not a recognized language name -
     *     never silently dropped or guessed
     */
    static String codeForName(String displayName) {
        String key = displayName == null ? null : displayName.trim().toLowerCase(java.util.Locale.ROOT);
        String code = key == null ? null : CODE_BY_LOWERCASE_NAME.get(key);
        if (code == null) {
            throw new IllegalArgumentException(
                    "Unrecognized candidate profile language name (cannot map to an ISO code): " + displayName);
        }
        return code;
    }

    /**
     * @throws IllegalArgumentException if {@code languageCode} has no known display name - should
     *     be unreachable for any code this same table produced via {@link #codeForName}
     */
    static String nameForCode(String languageCode) {
        String name = languageCode == null ? null : NAME_BY_CODE.get(languageCode);
        if (name == null) {
            throw new IllegalArgumentException(
                    "Unrecognized candidate profile language code (no known display name): " + languageCode);
        }
        return name;
    }

    private static String capitalize(String lowercase) {
        return Character.toUpperCase(lowercase.charAt(0)) + lowercase.substring(1);
    }
}
