package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 11 Step 1 correction: assembles the PostgreSQL-backed {@link CandidateProfileAggregate}
 * into the complete, lossless {@link CandidateProfileFacts} - the "full framework-free candidate
 * profile snapshot" stage of the {@code CandidateProfileAggregate -> CandidateProfileFacts ->
 * CandidateContextSnapshot -> use-case projections} pipeline. {@link
 * CandidateProfileAnalysisAssembler#toAnalysisProfile(CandidateProfileFacts)} is the explicit,
 * separate narrowing step for vacancy analysis specifically; this class performs no narrowing at
 * all - every currently-persisted fact (including skill {@link CandidateSkillFacts#category} and
 * language {@link CandidateLanguageFacts#proficiency}, both dropped by that narrowing step) survives
 * here.
 *
 * <p>Framework-free and stateless: no repository calls, no Spring dependencies. Reuses {@link
 * CandidateProfileLanguageCodes#nameForCode} (package-private, same package) to resolve each
 * persisted language code to its display name - the same resolution {@link
 * CandidateProfileAnalysisAssembler} used to perform directly before this correction.
 */
public final class CandidateProfileFactsAssembler {

    private CandidateProfileFactsAssembler() {
    }

    public static CandidateProfileFacts toProfileFacts(CandidateProfileAggregate aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Source candidate profile aggregate must not be null");
        }
        return new CandidateProfileFacts(
                aggregate.targetRole(),
                aggregate.seniority(),
                toSkillFacts(aggregate.skills()),
                toLanguageFacts(aggregate.languages()),
                aggregate.experienceYears(),
                toPreferences(aggregate),
                aggregate.email(),
                aggregate.phone(),
                aggregate.linkedinUrl(),
                aggregate.cvLocation(),
                aggregate.cvHeadline(),
                toEducationFacts(aggregate.education()));
    }

    private static List<CandidateEducationFacts> toEducationFacts(List<CandidateEducation> education) {
        return education.stream()
                .map(entry -> new CandidateEducationFacts(
                        entry.id(), entry.institution(), entry.degree(), entry.fieldOfStudy(), entry.location(),
                        entry.startDate(), entry.endDate(), entry.description(), entry.displayOrder()))
                .toList();
    }

    private static List<CandidateSkillFacts> toSkillFacts(List<CandidateSkill> skills) {
        return skills.stream()
                .map(skill -> new CandidateSkillFacts(skill.id(), skill.name(), skill.category(), skill.note(), skill.proficiency()))
                .toList();
    }

    private static List<CandidateLanguageFacts> toLanguageFacts(List<CandidateLanguage> languages) {
        return languages.stream()
                .map(language -> new CandidateLanguageFacts(
                        CandidateProfileLanguageCodes.nameForCode(language.languageCode()), language.proficiency()))
                .toList();
    }

    /** Moved unchanged from {@link CandidateProfileAnalysisAssembler} - this mapping was already lossless. */
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
     * {@code preferences}' incoming order - this class is framework-free and must not depend on a
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

    /** {@code CONTRACT_TYPE} rows all share one importance (see {@code CandidateProfileYamlImportMapper}); any row's value is representative. */
    private static PreferenceImportance importanceOfType(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        return preferences.stream()
                .filter(preference -> preference.type() == type)
                .map(CandidateProfilePreference::importance)
                .findFirst()
                .orElse(null);
    }
}
