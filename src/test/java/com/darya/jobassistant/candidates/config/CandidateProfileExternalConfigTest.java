package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.SkillProficiency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Exercises the real {@code spring.config.import}/{@code CANDIDATE_PROFILE_PATH} mechanism from
 * {@code application.yml} - {@link ConfigDataApplicationContextInitializer} makes
 * {@link ApplicationContextRunner} process config data (imports, placeholders) the same way a
 * real {@code SpringApplication} startup would, without needing a database or the full app.
 */
class CandidateProfileExternalConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class);

    @Test
    @SuppressWarnings("unchecked")
    void applicationYml_noLongerEmbedsTheCandidateProfile() throws IOException {
        Map<String, Object> applicationYml;
        try (var input = new ClassPathResource("application.yml").getInputStream()) {
            applicationYml = new Yaml().load(input);
        }

        assertThat(applicationYml).doesNotContainKey("candidate");
        Map<String, Object> spring = (Map<String, Object>) applicationYml.get("spring");
        Map<String, Object> config = (Map<String, Object>) spring.get("config");
        assertThat(config.get("import").toString()).contains("CANDIDATE_PROFILE_PATH");
    }

    @Test
    void defaultConfigDataImport_bindsFromTheGradleWiredTestFixture() {
        // Relies on the `test` task's CANDIDATE_PROFILE_PATH system property (build.gradle)
        // pointing at src/test/resources/candidate-profile-test.yml - never the developer's
        // real, gitignored config/candidate-profile.yml.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            CandidateProfileProperties properties = context.getBean(CandidateProfileProperties.class);
            assertThat(properties.targetRole()).isEqualTo("Senior Java Backend Engineer");
            assertThat(properties.skills()).extracting(
                            CandidateProfileProperties.SkillProperties::name,
                            CandidateProfileProperties.SkillProperties::proficiency)
                    .contains(
                            Tuple.tuple("Java", SkillProficiency.EXPERT),
                            Tuple.tuple("AWS", SkillProficiency.BASIC));
        });
    }

    @Test
    void customCandidateProfilePath_overridesTheDefaultLocation(@TempDir Path tempDir) throws IOException {
        Path customProfile = tempDir.resolve("custom-candidate-profile.yml");
        Files.writeString(customProfile, """
                candidate:
                  target-role: Custom Test Role
                  target-seniority: Mid
                  experience-years: 3
                  languages:
                    - English
                  skills:
                    - name: Kotlin
                      proficiency: WORKING
                  preferences:
                    preferred-work-arrangement: Hybrid
                    work-arrangement-importance: NEUTRAL
                """);

        contextRunner.withPropertyValues("CANDIDATE_PROFILE_PATH=" + customProfile.toAbsolutePath())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CandidateProfileProperties properties = context.getBean(CandidateProfileProperties.class);
                    assertThat(properties.targetRole()).isEqualTo("Custom Test Role");
                    assertThat(properties.skills()).extracting(CandidateProfileProperties.SkillProperties::name)
                            .containsExactly("Kotlin");
                });
    }

    @Test
    void missingCandidateProfileFile_failsContextStartupFastInsteadOfProducingAFakeProfile() {
        contextRunner.withPropertyValues("CANDIDATE_PROFILE_PATH=/nonexistent/candidate-profile-does-not-exist.yml")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(CandidateProfileProperties.class)
    static class TestConfig {
    }
}
