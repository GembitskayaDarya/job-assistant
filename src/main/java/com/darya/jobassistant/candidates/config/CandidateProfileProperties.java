package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring-binding shape for the {@code candidate.*} configuration tree. Kept separate from the
 * framework-independent {@code candidates} domain records ({@code CandidateProfile}, {@code
 * CandidateSkill}, {@code CandidatePreferences}) - this record's only job is relaxed property
 * binding; {@link YamlCandidateProfileMigrationSource} maps it into the domain model.
 *
 * <p>Sprint 9 Step 4: deliberately unvalidated (no {@code @Validated}/JSR-303 constraints).
 * {@code spring.config.import} for {@code candidate-profile.yml} is {@code optional:}, so this
 * bean must always bind successfully - including with every field left {@code null}/default when
 * the file is entirely absent, which is the normal case at runtime now that PostgreSQL is the
 * source of truth. {@link YamlCandidateProfileMigrationSource} performs the actual "is this a
 * real profile" validation explicitly, and only when migration mode is active.
 */
@ConfigurationProperties(prefix = "candidate")
public record CandidateProfileProperties(
        String targetRole,
        String targetSeniority,
        List<SkillProperties> skills,
        List<String> languages,
        int experienceYears,
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
