package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 7 correction (item 3): proves the complete Candidate Profile migration / Career
 * History import startup-mode compatibility matrix via {@link CareerHistoryStartupExclusivityValidator}
 * - fast, {@link ApplicationContextRunner}-based (no database), matching {@code
 * CareerHistoryImportActivationTest}'s convention. See {@code
 * CareerHistoryImportStartupOrderingTest} for the real-{@code SpringApplication} proof that a
 * conflicting combination fails before any runner executes or any row is written.
 */
class CareerHistoryStartupExclusivityValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, CareerHistoryStartupExclusivityValidator.class);

    @ParameterizedTest(name = "candidateProfileMigration={0}, careerHistoryImport={1} -> allowed")
    @CsvSource({
            "OFF, OFF",
            "OFF, DRY_RUN",
            "OFF, APPLY",
            "DRY_RUN, OFF",
            "APPLY, OFF",
    })
    void allowedCombination_contextStartsSuccessfully(String migrationMode, String importMode) {
        contextRunner.withPropertyValues(
                "candidate-profile.migration.mode=" + migrationMode,
                "career-history.import.mode=" + importMode).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void migrationModeAbsent_importModeActive_isAllowed() {
        contextRunner.withPropertyValues("career-history.import.mode=DRY_RUN")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bothModesAbsent_validatorBeanDoesNotExist() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CareerHistoryStartupExclusivityValidator.class));
    }

    @Test
    void migrationActive_importInactive_validatorBeanDoesNotExist() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=APPLY")
                .run(context -> assertThat(context).doesNotHaveBean(CareerHistoryStartupExclusivityValidator.class));
    }

    @ParameterizedTest(name = "candidateProfileMigration={0}, careerHistoryImport={1} -> startup configuration failure")
    @CsvSource({
            "DRY_RUN, DRY_RUN",
            "DRY_RUN, APPLY",
            "APPLY, DRY_RUN",
            "APPLY, APPLY",
    })
    void conflictingCombination_contextFailsWithFocusedException(String migrationMode, String importMode) {
        contextRunner.withPropertyValues(
                "candidate-profile.migration.mode=" + migrationMode,
                "career-history.import.mode=" + importMode).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context).getFailure().hasRootCauseInstanceOf(CareerHistoryImportStartupConflictException.class);
        });
    }

    @Configuration
    @AutoConfigurationPackage
    @EnableConfigurationProperties(CandidateProfileMigrationProperties.class)
    static class TestConfig {
    }
}
