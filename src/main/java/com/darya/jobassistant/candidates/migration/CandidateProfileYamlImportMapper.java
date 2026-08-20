package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateEducationEntry;
import com.darya.jobassistant.candidates.CandidateLanguageEntry;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the YAML-oriented, analysis-ready {@link CandidateProfile} to the PostgreSQL-backed
 * {@link CandidateProfileAggregate} - the migration direction. See {@link
 * CandidateProfileAnalysisAssembler} for the opposite direction (used for both the future
 * provider switch and this step's own parity verification); the two are deliberately separate
 * classes, not one ambiguous bidirectional mapper.
 *
 * <p>Framework-free and stateless: no repository calls, no Spring dependencies. Always produces a
 * not-yet-persisted aggregate ({@code id = null}, {@code version = 0}) - the caller ({@link
 * CandidateProfileMigrationUseCase}) decides whether that becomes an insert or is merged with an
 * existing aggregate's identity/version for an update.
 *
 * <p>Four Step 1 flat {@code CandidateProfileAggregate} fields are deliberately left {@code null}
 * here rather than populated from {@code source} - see {@link CandidateProfileAggregate}'s
 * javadoc for why: {@code preferredCompanyType}/{@code remotePolicy} would contradict the
 * lossless {@code COMPANY_TYPE}/{@code WORK_ARRANGEMENT} preferences this mapper also produces,
 * and {@code preferredLocation}/{@code employmentModel} have no lossless source value in the YAML
 * model at all.
 */
public final class CandidateProfileYamlImportMapper {

    private CandidateProfileYamlImportMapper() {
    }

    public static CandidateProfileAggregate toAggregate(CandidateProfile source, String profileKey) {
        if (source == null) {
            throw new IllegalArgumentException("Source candidate profile must not be null");
        }
        return new CandidateProfileAggregate(
                null,
                profileKey,
                source.targetRole(),
                source.targetSeniority(),
                source.experienceYears(),
                null,
                null,
                null,
                null,
                null,
                null,
                source.preferences().currentCountry(),
                source.preferences().relocationAllowed(),
                source.preferences().salaryExpectation(),
                source.fullName(),
                source.email(),
                source.phone(),
                source.linkedinUrl(),
                source.cvLocation(),
                source.cvHeadline(),
                toSkills(source.skills()),
                toLanguages(source.languages()),
                toPreferences(source.preferences()),
                toEducation(source.education()),
                0L);
    }

    private static List<CandidateSkill> toSkills(List<com.darya.jobassistant.candidates.CandidateSkill> skills) {
        return skills.stream()
                .map(skill -> new CandidateSkill(skill.name(), null, skill.note(), skill.proficiency()))
                .toList();
    }

    /**
     * {@code displayOrder} is assigned from this list's own position (Sprint 11 Step 5) - the
     * YAML {@code languages:} list order is the intended CV presentation order, the same
     * convention already used for {@code preferredContractTypes}. {@link CandidateLanguageEntry
     * #proficiency} is carried through unchanged (acceptance correction) - the previous plain
     * {@code List<String>} shape had no field to carry it, so it was always imported as {@code
     * null}.
     */
    private static List<CandidateLanguage> toLanguages(List<CandidateLanguageEntry> languages) {
        List<CandidateLanguage> result = new ArrayList<>();
        for (int i = 0; i < languages.size(); i++) {
            CandidateLanguageEntry entry = languages.get(i);
            result.add(new CandidateLanguage(CandidateProfileLanguageCodes.codeForName(entry.language()), entry.proficiency(), i));
        }
        return result;
    }

    /** {@code displayOrder} is assigned from this list's own position, same convention as {@link #toLanguages}. */
    private static List<CandidateEducation> toEducation(List<CandidateEducationEntry> education) {
        List<CandidateEducation> result = new ArrayList<>();
        for (int i = 0; i < education.size(); i++) {
            CandidateEducationEntry entry = education.get(i);
            result.add(new CandidateEducation(
                    entry.institution(), entry.degree(), entry.fieldOfStudy(), entry.location(),
                    entry.startDate(), entry.endDate(), entry.description(), i));
        }
        return result;
    }

    private static List<CandidateProfilePreference> toPreferences(CandidatePreferences preferences) {
        List<CandidateProfilePreference> result = new ArrayList<>();
        if (preferences.preferredWorkArrangement() != null) {
            result.add(new CandidateProfilePreference(
                    CandidatePreferenceType.WORK_ARRANGEMENT,
                    preferences.preferredWorkArrangement(),
                    preferences.workArrangementImportance()));
        }
        for (String country : preferences.allowedWorkCountries()) {
            result.add(new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, country, null));
        }
        List<String> contractTypes = preferences.preferredContractTypes();
        for (int i = 0; i < contractTypes.size(); i++) {
            result.add(new CandidateProfilePreference(
                    CandidatePreferenceType.CONTRACT_TYPE, contractTypes.get(i), preferences.contractTypeImportance(), i));
        }
        if (preferences.preferredCompanyType() != null) {
            result.add(new CandidateProfilePreference(
                    CandidatePreferenceType.COMPANY_TYPE,
                    preferences.preferredCompanyType(),
                    preferences.companyTypeImportance()));
        }
        return result;
    }
}
