package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Assembles the PostgreSQL-backed {@link CandidateProfileAggregate} into the YAML-oriented,
 * analysis-ready {@link CandidateProfile} - the future-provider-switch direction. See {@link
 * CandidateProfileYamlImportMapper} for the opposite (migration) direction; the two are
 * deliberately separate classes.
 *
 * <p>This is the acceptance gate for a later Sprint 9 step's provider switch:
 * {@code JobAnalysisService} would consume exactly this assembler's output once {@code
 * CandidateProfileProvider} is switched to read from {@link
 * com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort} instead of YAML -
 * not implemented in this step. No persistence id or version leaks into the result: {@code
 * CandidateProfile} has no such fields at all.
 *
 * <p>Framework-free and stateless: no repository calls, no Spring dependencies.
 */
public final class CandidateProfileAnalysisAssembler {

    private CandidateProfileAnalysisAssembler() {
    }

    public static CandidateProfile toAnalysisProfile(CandidateProfileAggregate aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Source candidate profile aggregate must not be null");
        }
        return new CandidateProfile(
                aggregate.targetRole(),
                aggregate.seniority(),
                toSkills(aggregate.skills()),
                toLanguageNames(aggregate.languages()),
                aggregate.experienceYears(),
                toPreferences(aggregate));
    }

    private static List<com.darya.jobassistant.candidates.CandidateSkill> toSkills(List<CandidateSkill> skills) {
        return skills.stream()
                .map(skill -> new com.darya.jobassistant.candidates.CandidateSkill(skill.name(), skill.proficiency(), skill.note()))
                .toList();
    }

    private static List<String> toLanguageNames(List<CandidateLanguage> languages) {
        return languages.stream()
                .map(language -> CandidateProfileLanguageCodes.nameForCode(language.languageCode()))
                .toList();
    }

    private static CandidatePreferences toPreferences(CandidateProfileAggregate aggregate) {
        List<CandidateProfilePreference> preferences = aggregate.preferences();

        Optional<CandidateProfilePreference> workArrangement = findSingle(preferences, CandidatePreferenceType.WORK_ARRANGEMENT);
        Optional<CandidateProfilePreference> companyType = findSingle(preferences, CandidatePreferenceType.COMPANY_TYPE);
        List<String> allowedWorkCountries = valuesOfType(preferences, CandidatePreferenceType.ALLOWED_WORK_COUNTRY);
        List<String> contractTypes = valuesOfType(preferences, CandidatePreferenceType.CONTRACT_TYPE);
        PreferenceImportance contractTypeImportance = importanceOfType(preferences, CandidatePreferenceType.CONTRACT_TYPE);

        return new CandidatePreferences(
                aggregate.currentCountry(),
                workArrangement.map(CandidateProfilePreference::value).orElse(null),
                workArrangement.map(CandidateProfilePreference::importance).orElse(null),
                allowedWorkCountries,
                aggregate.relocationAllowed(),
                contractTypes,
                contractTypeImportance,
                companyType.map(CandidateProfilePreference::value).orElse(null),
                companyType.map(CandidateProfilePreference::importance).orElse(null),
                aggregate.salaryExpectationNote());
    }

    private static Optional<CandidateProfilePreference> findSingle(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        return preferences.stream().filter(preference -> preference.type() == type).findFirst();
    }

    /**
     * For an order-significant {@code type} (see {@link CandidatePreferenceType#isOrderSignificant()}),
     * explicitly sorts by {@link CandidateProfilePreference#priorityOrder()} rather than trusting
     * {@code preferences}' incoming order - the caller ({@link
     * com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter}) already
     * returns rows in that order, but this class is framework-free and must not depend on a
     * persistence-layer convention holding elsewhere to reconstruct the original source list
     * exactly. Order-insignificant types are returned in whatever order they arrive in - callers
     * must not read anything into that order (see the type's own javadoc).
     */
    private static List<String> valuesOfType(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        var stream = preferences.stream().filter(preference -> preference.type() == type);
        if (type.isOrderSignificant()) {
            stream = stream.sorted(Comparator.comparing(CandidateProfilePreference::priorityOrder));
        }
        return stream.map(CandidateProfilePreference::value).toList();
    }

    /** {@code CONTRACT_TYPE} rows all share one importance (see {@link CandidateProfileYamlImportMapper}); any row's value is representative. */
    private static PreferenceImportance importanceOfType(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        return preferences.stream()
                .filter(preference -> preference.type() == type)
                .map(CandidateProfilePreference::importance)
                .findFirst()
                .orElse(null);
    }
}
