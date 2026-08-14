package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationSourceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlCandidateProfileMigrationSourceTest {

    @Test
    void loadSourceProfile_mapsEveryPropertyToCandidateProfile() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer",
                "Senior",
                List.of(
                        new CandidateProfileProperties.SkillProperties("Java", SkillProficiency.WORKING, "Primary language"),
                        new CandidateProfileProperties.SkillProperties("Spring Boot", SkillProficiency.WORKING, null),
                        new CandidateProfileProperties.SkillProperties("Kafka", SkillProficiency.BASIC, null)),
                List.of("English", "Russian", "Polish"),
                6,
                new CandidateProfileProperties.PreferencesProperties(
                        "Poland",
                        "Remote",
                        PreferenceImportance.STRONG,
                        List.of("Poland"),
                        false,
                        List.of("B2B"),
                        PreferenceImportance.PREFERRED,
                        "Product",
                        PreferenceImportance.PREFERRED,
                        null),
                null, null, null, null, null, List.of(), List.of());

        YamlCandidateProfileMigrationSource source = new YamlCandidateProfileMigrationSource(properties);

        CandidateProfile profile = source.loadSourceProfile();

        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Developer");
        assertThat(profile.targetSeniority()).isEqualTo("Senior");
        assertThat(profile.experienceYears()).isEqualTo(6);
        assertThat(profile.languages()).containsExactly("English", "Russian", "Polish");

        // Skill order is preserved exactly as configured.
        assertThat(profile.skills()).containsExactly(
                new CandidateSkill("Java", SkillProficiency.WORKING, "Primary language"),
                new CandidateSkill("Spring Boot", SkillProficiency.WORKING, null),
                new CandidateSkill("Kafka", SkillProficiency.BASIC, null));

        CandidatePreferences preferences = profile.preferences();
        assertThat(preferences.currentCountry()).isEqualTo("Poland");
        assertThat(preferences.preferredWorkArrangement()).isEqualTo("Remote");
        assertThat(preferences.workArrangementImportance()).isEqualTo(PreferenceImportance.STRONG);
        assertThat(preferences.allowedWorkCountries()).containsExactly("Poland");
        assertThat(preferences.relocationAllowed()).isFalse();
        assertThat(preferences.preferredContractTypes()).containsExactly("B2B");
        assertThat(preferences.contractTypeImportance()).isEqualTo(PreferenceImportance.PREFERRED);
        assertThat(preferences.preferredCompanyType()).isEqualTo("Product");
        assertThat(preferences.companyTypeImportance()).isEqualTo(PreferenceImportance.PREFERRED);
        assertThat(preferences.salaryExpectation()).isNull();
    }

    @Test
    void loadSourceProfile_blankOptionalPreferenceText_becomesNull() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer",
                "Senior",
                List.of(),
                List.of("English"),
                6,
                new CandidateProfileProperties.PreferencesProperties(
                        "   ", "  ", null, List.of(), false, List.of(), null, "   ", null, "  "),
                null, null, null, null, null, List.of(), List.of());

        CandidateProfile profile = new YamlCandidateProfileMigrationSource(properties).loadSourceProfile();

        CandidatePreferences preferences = profile.preferences();
        assertThat(preferences.currentCountry()).isNull();
        assertThat(preferences.preferredWorkArrangement()).isNull();
        assertThat(preferences.preferredCompanyType()).isNull();
        assertThat(preferences.salaryExpectation()).isNull();
    }

    @Test
    void loadSourceProfile_missingPreferences_defaultsToEmptyUnconfiguredPreferences() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer", "Senior", List.of(), List.of("English"), 6, null,
                null, null, null, null, null, List.of(), List.of());

        CandidateProfile profile = new YamlCandidateProfileMigrationSource(properties).loadSourceProfile();

        assertThat(profile.preferences()).isNotNull();
        assertThat(profile.preferences().allowedWorkCountries()).isEmpty();
        assertThat(profile.preferences().preferredContractTypes()).isEmpty();
    }

    @Test
    void loadSourceProfile_returnsImmutableCollections() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer",
                "Senior",
                List.of(new CandidateProfileProperties.SkillProperties("Java", SkillProficiency.WORKING, null)),
                List.of("English"),
                6,
                new CandidateProfileProperties.PreferencesProperties(
                        "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland"), false,
                        List.of("B2B"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED, null),
                null, null, null, null, null, List.of(), List.of());
        YamlCandidateProfileMigrationSource source = new YamlCandidateProfileMigrationSource(properties);

        CandidateProfile profile = source.loadSourceProfile();

        assertThatThrownBy(() -> profile.skills().add(new CandidateSkill("Kotlin", SkillProficiency.BASIC, null)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.languages().add("German")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.preferences().allowedWorkCountries().add("Germany"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profile.preferences().preferredContractTypes().add("UoP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loadSourceProfile_missingTargetRole_throwsMigrationSourceException() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                null, "Senior", List.of(), List.of("English"), 6, null,
                null, null, null, null, null, List.of(), List.of());

        assertThatThrownBy(() -> new YamlCandidateProfileMigrationSource(properties).loadSourceProfile())
                .isInstanceOf(CandidateProfileMigrationSourceException.class);
    }

    @Test
    void loadSourceProfile_blankTargetSeniority_throwsMigrationSourceException() {
        CandidateProfileProperties properties = new CandidateProfileProperties(
                "Senior Java Backend Developer", "   ", List.of(), List.of("English"), 6, null,
                null, null, null, null, null, List.of(), List.of());

        assertThatThrownBy(() -> new YamlCandidateProfileMigrationSource(properties).loadSourceProfile())
                .isInstanceOf(CandidateProfileMigrationSourceException.class);
    }

    @Test
    void loadSourceProfile_entirelyUnboundProperties_throwsMigrationSourceExceptionRatherThanEmptyProfile() {
        // Simulates the "candidate-profile.yml missing, optional import silently skipped" case:
        // every field left at its default/null.
        CandidateProfileProperties properties = new CandidateProfileProperties(
                null, null, null, null, 0, null,
                null, null, null, null, null, List.of(), List.of());

        assertThatThrownBy(() -> new YamlCandidateProfileMigrationSource(properties).loadSourceProfile())
                .isInstanceOf(CandidateProfileMigrationSourceException.class);
    }
}
