package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CandidateProfilePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void binding_mapsAllValuesFromProperties() {
        contextRunner
                .withPropertyValues(
                        "candidate.target-role=Senior Java Backend Developer",
                        "candidate.target-seniority=Senior",
                        "candidate.skills[0].name=Java",
                        "candidate.skills[0].proficiency=WORKING",
                        "candidate.skills[0].note=Primary language",
                        "candidate.skills[1].name=Spring Boot",
                        "candidate.skills[1].proficiency=WORKING",
                        "candidate.languages=English,Polish",
                        "candidate.experience-years=6",
                        "candidate.preferences.current-country=Poland",
                        "candidate.preferences.preferred-work-arrangement=Remote",
                        "candidate.preferences.work-arrangement-importance=STRONG",
                        "candidate.preferences.allowed-work-countries=Poland",
                        "candidate.preferences.relocation-allowed=false",
                        "candidate.preferences.preferred-contract-types=B2B",
                        "candidate.preferences.contract-type-importance=PREFERRED",
                        "candidate.preferences.preferred-company-type=Product",
                        "candidate.preferences.company-type-importance=PREFERRED")
                // salary-expectation intentionally left unset: verifies missing/blank salary
                // expectation binds to null rather than requiring a value.
                .run(context -> {
                    CandidateProfileProperties properties = context.getBean(CandidateProfileProperties.class);
                    assertThat(properties.targetRole()).isEqualTo("Senior Java Backend Developer");
                    assertThat(properties.targetSeniority()).isEqualTo("Senior");
                    assertThat(properties.experienceYears()).isEqualTo(6);
                    assertThat(properties.languages()).containsExactly("English", "Polish");

                    assertThat(properties.skills()).hasSize(2);
                    CandidateProfileProperties.SkillProperties firstSkill = properties.skills().get(0);
                    assertThat(firstSkill.name()).isEqualTo("Java");
                    assertThat(firstSkill.proficiency()).isEqualTo(SkillProficiency.WORKING);
                    assertThat(firstSkill.note()).isEqualTo("Primary language");
                    assertThat(properties.skills().get(1).name()).isEqualTo("Spring Boot");

                    CandidateProfileProperties.PreferencesProperties preferences = properties.preferences();
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
                });
    }

    @Test
    void constructor_defensivelyCopiesSkillsAndLanguages() {
        List<CandidateProfileProperties.SkillProperties> skills =
                new ArrayList<>(List.of(new CandidateProfileProperties.SkillProperties("Java", SkillProficiency.WORKING, null)));
        List<String> languages = new ArrayList<>(List.of("English"));

        CandidateProfileProperties properties =
                new CandidateProfileProperties("Senior Java Backend Developer", "Senior", skills, languages, 6, null);

        skills.add(new CandidateProfileProperties.SkillProperties("Kotlin", SkillProficiency.BASIC, null));
        languages.add("Polish");

        assertThat(properties.skills()).hasSize(1);
        assertThat(properties.languages()).containsExactly("English");
        assertThat(properties.skills()).isUnmodifiable();
        assertThat(properties.languages()).isUnmodifiable();
    }

    /**
     * Sprint 9 Step 4: this record is deliberately unvalidated now - {@code spring.config.import}
     * for {@code candidate-profile.yml} is {@code optional:}, so normal PostgreSQL runtime must
     * never fail to bind this properties bean just because the file is absent. Required-field
     * enforcement moved to {@code YamlCandidateProfileMigrationSource.loadSourceProfile()}, which
     * only ever runs during explicit migration - see {@code YamlCandidateProfileMigrationSourceTest}.
     */
    @Test
    void binding_blankTargetRole_bindsSuccessfully_validationMovedToMigrationSource() {
        contextRunner
                .withPropertyValues(
                        "candidate.target-role=",
                        "candidate.target-seniority=Senior",
                        "candidate.languages=English",
                        "candidate.experience-years=6")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CandidateProfileProperties.class).targetRole()).isEmpty();
                });
    }

    @Test
    void binding_entirelyAbsentProperties_bindsSuccessfullyWithNullDefaults() {
        // Simulates the "candidate-profile.yml missing, optional config import silently skipped"
        // case - normal runtime must never fail to bind this bean just because of that.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            CandidateProfileProperties properties = context.getBean(CandidateProfileProperties.class);
            assertThat(properties.targetRole()).isNull();
            assertThat(properties.targetSeniority()).isNull();
            assertThat(properties.skills()).isEmpty();
            assertThat(properties.languages()).isEmpty();
            assertThat(properties.experienceYears()).isZero();
        });
    }

    @EnableConfigurationProperties(CandidateProfileProperties.class)
    static class TestConfig {
    }
}
