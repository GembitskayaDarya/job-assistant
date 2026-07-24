package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spring-binding shape for the {@code candidate.*} configuration tree. Kept separate from the
 * framework-independent {@code candidates} domain records ({@code CandidateProfile}, {@code
 * CandidateSkill}, {@code CandidatePreferences}) - this record's only job is relaxed property
 * binding; {@link ConfigurationCandidateProfileProvider} maps it into the domain model, where
 * validation and normalization that must not depend on Spring actually live.
 */
@ConfigurationProperties(prefix = "candidate")
@Validated
public record CandidateProfileProperties(
        @NotBlank String targetRole,
        @NotBlank String targetSeniority,
        List<SkillProperties> skills,
        List<String> languages,
        @PositiveOrZero int experienceYears,
        PreferencesProperties preferences
) {
    public CandidateProfileProperties {
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public record SkillProperties(
            String name,
            SkillProficiency proficiency,
            String note
    ) {
    }

    public record PreferencesProperties(
            String currentCountry,
            String preferredWorkArrangement,
            PreferenceImportance workArrangementImportance,
            List<String> allowedWorkCountries,
            boolean relocationAllowed,
            List<String> preferredContractTypes,
            PreferenceImportance contractTypeImportance,
            String preferredCompanyType,
            PreferenceImportance companyTypeImportance,
            String salaryExpectation
    ) {
        public PreferencesProperties {
            allowedWorkCountries = allowedWorkCountries == null ? List.of() : List.copyOf(allowedWorkCountries);
            preferredContractTypes = preferredContractTypes == null ? List.of() : List.copyOf(preferredContractTypes);
        }
    }
}
