package com.darya.jobassistant.candidates.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 4 correction: proves {@link YamlCandidateProfileMigrationSource}'s activation
 * rule without a real YAML file or database - it exists only for {@code
 * candidate-profile.migration.mode=DRY_RUN}/{@code =APPLY} (see {@code
 * CandidateProfileMigrationActiveCondition}, matching {@code CandidateProfileMigrationRunner}'s
 * own condition), never for an absent property or {@code OFF}, and it never implements the
 * runtime {@link CandidateProfileProvider} abstraction.
 */
class YamlCandidateProfileMigrationSourceActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, YamlCandidateProfileMigrationSource.class);

    @Test
    void migrationPropertyAbsent_sourceBeanDoesNotExist() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(YamlCandidateProfileMigrationSource.class));
    }

    @Test
    void modeOff_sourceBeanDoesNotExist() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=OFF")
                .run(context -> assertThat(context).doesNotHaveBean(YamlCandidateProfileMigrationSource.class));
    }

    @Test
    void modeApply_sourceBeanExists() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=APPLY")
                .run(context -> assertThat(context).hasSingleBean(YamlCandidateProfileMigrationSource.class));
    }

    @Test
    void modeDryRun_sourceBeanExists() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=DRY_RUN")
                .run(context -> assertThat(context).hasSingleBean(YamlCandidateProfileMigrationSource.class));
    }

    @Test
    void sourceBean_isNeverAssignableToRuntimeCandidateProfileProvider() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=DRY_RUN")
                .run(context -> {
                    assertThat(context).hasSingleBean(YamlCandidateProfileMigrationSource.class);
                    assertThat(context).doesNotHaveBean(CandidateProfileProvider.class);
                    assertThat(CandidateProfileProvider.class.isAssignableFrom(YamlCandidateProfileMigrationSource.class))
                            .isFalse();
                    assertThat(context.getBean(YamlCandidateProfileMigrationSource.class))
                            .isInstanceOf(CandidateProfileMigrationSource.class)
                            .isNotInstanceOf(CandidateProfileProvider.class);
                });
    }

    @Configuration
    @AutoConfigurationPackage
    @EnableConfigurationProperties(CandidateProfileProperties.class)
    static class TestConfig {
    }
}
