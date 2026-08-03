package com.darya.jobassistant.candidates.config;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationActiveCondition;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationSource;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationSourceException;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 4: the migration-only source of the YAML-oriented {@link CandidateProfile},
 * reading {@code candidate-profile.yml} (see {@link CandidateProfileProperties}) via Spring's
 * Config Data binding. This class no longer implements the runtime {@code
 * CandidateProfileProvider} - {@code PersistentCandidateProfileProvider} is the only runtime
 * source now, and {@link com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunner}
 * is the only caller of this class.
 *
 * <p>This bean exists only for {@code candidate-profile.migration.mode=DRY_RUN} or {@code
 * =APPLY} (see {@link CandidateProfileMigrationActiveCondition}, matching {@code
 * CandidateProfileMigrationRunner}'s own condition) - normal runtime, where that property is
 * absent or {@code OFF}, never constructs it and never touches {@code candidate-profile.yml}.
 *
 * <p>{@code spring.config.import} for that file is {@code optional:} (see {@code
 * application.yml}), so {@link CandidateProfileProperties} always binds successfully even when
 * the file is entirely missing - every field simply stays {@code null}/default. {@link
 * #loadSourceProfile()} is therefore the one place that turns "the file was missing or left the
 * required fields unset" into a clear, explicit {@link CandidateProfileMigrationSourceException}
 * rather than silently producing an empty/placeholder {@link CandidateProfile}.
 */
@Component
@Conditional(CandidateProfileMigrationActiveCondition.class)
public class YamlCandidateProfileMigrationSource implements CandidateProfileMigrationSource {

    private final CandidateProfileProperties properties;

    public YamlCandidateProfileMigrationSource(CandidateProfileProperties properties) {
        this.properties = properties;
    }

    @Override
    public CandidateProfile loadSourceProfile() {
        requireMinimumFields(properties);
        return new CandidateProfile(
                properties.targetRole(),
                properties.targetSeniority(),
                toSkills(properties.skills()),
                properties.languages(),
                properties.experienceYears(),
                toPreferences(properties.preferences()));
    }

    /**
     * The same minimum required-field check {@code @NotBlank} used to enforce eagerly at
     * binding time before Sprint 9 Step 4 - performed here explicitly instead, so it only ever
     * runs when migration is actually being exercised, never blocking normal PostgreSQL runtime.
     */
    private void requireMinimumFields(CandidateProfileProperties properties) {
        if (isBlank(properties.targetRole()) || isBlank(properties.targetSeniority())) {
            throw new CandidateProfileMigrationSourceException(
                    "Candidate profile migration YAML source is missing or invalid - target role and "
                            + "target seniority must both be set. Check CANDIDATE_PROFILE_PATH points at a "
                            + "valid candidate-profile.yml file.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
