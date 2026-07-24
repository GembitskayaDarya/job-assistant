package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.CandidateSkill;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationCandidateProfileProvider implements CandidateProfileProvider {

    private final CandidateProfileProperties properties;

    public ConfigurationCandidateProfileProvider(CandidateProfileProperties properties) {
        this.properties = properties;
    }

    @Override
    public CandidateProfile getProfile() {
        return new CandidateProfile(
                properties.targetRole(),
                properties.targetSeniority(),
                toSkills(properties.skills()),
                properties.languages(),
                properties.experienceYears(),
                toPreferences(properties.preferences()));
    }

    private List<CandidateSkill> toSkills(List<CandidateProfileProperties.SkillProperties> skills) {
        return skills.stream()
                .map(skill -> new CandidateSkill(skill.name(), skill.proficiency(), skill.note()))
                .toList();
    }

    private CandidatePreferences toPreferences(CandidateProfileProperties.PreferencesProperties preferences) {
        if (preferences == null) {
            return new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
        }
        return new CandidatePreferences(
                blankToNull(preferences.currentCountry()),
                blankToNull(preferences.preferredWorkArrangement()),
                preferences.workArrangementImportance(),
                preferences.allowedWorkCountries(),
                preferences.relocationAllowed(),
                preferences.preferredContractTypes(),
                preferences.contractTypeImportance(),
                blankToNull(preferences.preferredCompanyType()),
                preferences.companyTypeImportance(),
                blankToNull(preferences.salaryExpectation()));
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
