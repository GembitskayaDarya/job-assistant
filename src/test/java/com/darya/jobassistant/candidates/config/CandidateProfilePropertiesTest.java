package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;

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
                        "candidate.skills=Java,Spring Boot,PostgreSQL",
                        "candidate.languages=English,Polish",
                        "candidate.experience-years=6",
                        "candidate.preferred-company-type=Product company",
                        "candidate.preferred-location=Remote Europe")
                .run(context -> {
                    CandidateProfileProperties properties = context.getBean(CandidateProfileProperties.class);
                    assertThat(properties.targetRole()).isEqualTo("Senior Java Backend Developer");
                    assertThat(properties.skills()).containsExactly("Java", "Spring Boot", "PostgreSQL");
                    assertThat(properties.languages()).containsExactly("English", "Polish");
                    assertThat(properties.experienceYears()).isEqualTo(6);
                    assertThat(properties.preferredCompanyType()).isEqualTo("Product company");
                    assertThat(properties.preferredLocation()).isEqualTo("Remote Europe");
                });
    }

    @Test
    void constructor_defensivelyCopiesSkillsAndLanguages() {
        List<String> skills = new ArrayList<>(List.of("Java"));
        List<String> languages = new ArrayList<>(List.of("English"));

        CandidateProfileProperties properties =
                new CandidateProfileProperties("Senior Java Backend Developer", skills, languages, 6, "Product company", "Remote Europe");

        skills.add("Kotlin");
        languages.add("Polish");

        assertThat(properties.skills()).containsExactly("Java");
        assertThat(properties.languages()).containsExactly("English");
        assertThat(properties.skills()).isUnmodifiable();
        assertThat(properties.languages()).isUnmodifiable();
    }

    @Test
    void binding_blankTargetRole_failsValidation() {
        contextRunner
                .withPropertyValues(
                        "candidate.target-role=",
                        "candidate.skills=Java",
                        "candidate.languages=English",
                        "candidate.experience-years=6")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void binding_negativeExperienceYears_failsValidation() {
        contextRunner
                .withPropertyValues(
                        "candidate.target-role=Senior Java Backend Developer",
                        "candidate.skills=Java",
                        "candidate.languages=English",
                        "candidate.experience-years=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(CandidateProfileProperties.class)
    static class TestConfig {
    }
}
