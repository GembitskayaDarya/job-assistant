package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
        PreferencesProperties preferences,
        String email,
        String phone,
        String linkedinUrl,
        String cvLocation,
        String cvHeadline,
        List<EducationProperties> education,
        List<PersonalProjectProperties> personalProjects
) {
    public CandidateProfileProperties {
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
        education = education == null ? List.of() : List.copyOf(education);
        personalProjects = personalProjects == null ? List.of() : List.copyOf(personalProjects);
    }

    public record SkillProperties(
            String name,
            SkillProficiency proficiency,
            String note
    ) {
    }

    /** Sprint 11 Step 5: binding shape for one {@code candidate.education[]} entry - only {@code institution} is required. */
    public record EducationProperties(
            String institution,
            String degree,
            String fieldOfStudy,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            String description
    ) {
    }

    /**
     * Sprint 11 Step 5: binding shape for one {@code candidate.personal-projects[]} entry -
     * factual source data only, matched to {@code
     * personalprojects.migration.PersonalProjectYamlImportMapper}'s output shape. Not part of
     * {@code CandidateProfile} (that type is a projection of {@code CandidateProfileAggregate}
     * alone) - Personal Projects are their own independent aggregate, so this binding is mapped
     * directly to {@code personalprojects.aggregate.PersonalProject} instead.
     *
     * <p>{@link #id} (acceptance correction) is a stable, human-authored UUID the private YAML
     * assigns to this project - the bootstrap import's sole identity key. A project's human-readable
     * {@link #name} is deliberately never used as identity (it can legitimately change without the
     * project becoming "a different project"); {@link #id} is required for import to be idempotent -
     * see {@code personalprojects.migration.PersonalProjectImportUseCase}.
     */
    public record PersonalProjectProperties(
            UUID id,
            String name,
            String description,
            String url,
            LocalDate startDate,
            LocalDate endDate,
            List<String> highlights,
            List<TechnologyProperties> technologies
    ) {
        public PersonalProjectProperties {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
            technologies = technologies == null ? List.of() : List.copyOf(technologies);
        }
    }

    public record TechnologyProperties(
            String name,
            String category
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
