package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 3: proves {@link CandidateProfileMigrationRunner}'s activation rules without a
 * real database - {@link ApplicationContextRunner} builds a minimal context with mocked {@link
 * CandidateProfileMigrationUseCase}/{@link CandidateProfileProvider} beans, so this exercises
 * exactly the {@code @ConditionalOnProperty}/property-binding behavior in isolation.
 */
class CandidateProfileMigrationRunnerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockDependenciesConfig.class, CandidateProfileMigrationRunner.class);

    @Test
    void propertyAbsent_runnerBeanDoesNotExist() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CandidateProfileMigrationRunner.class));
    }

    @Test
    void modeOff_runnerBeanExists_butCallsNoMigrationUseCaseMethod() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=OFF").run(context -> {
            assertThat(context).hasSingleBean(CandidateProfileMigrationRunner.class);
            context.getBean(CandidateProfileMigrationRunner.class).runMigration();
            verifyNoInteractions(context.getBean(CandidateProfileMigrationUseCase.class));
        });
    }

    @Test
    void modeDryRun_callsUseCaseDryRunExactlyOnce_withConfiguredProfileKey() {
        contextRunner.withPropertyValues(
                "candidate-profile.migration.mode=DRY_RUN",
                "candidate-profile.migration.profile-key=primary").run(context -> {
            context.getBean(CandidateProfileMigrationRunner.class).runMigration();
            CandidateProfileMigrationUseCase useCase = context.getBean(CandidateProfileMigrationUseCase.class);
            verify(useCase, times(1)).dryRun(any(CandidateProfile.class), eq("primary"));
            long applyInvocations = mockingDetails(useCase).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("apply"))
                    .count();
            assertThat(applyInvocations).isZero();
        });
    }

    @Test
    void modeApply_callsUseCaseApplyExactlyOnce() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=APPLY").run(context -> {
            context.getBean(CandidateProfileMigrationRunner.class).runMigration();
            CandidateProfileMigrationUseCase useCase = context.getBean(CandidateProfileMigrationUseCase.class);
            verify(useCase, times(1)).apply(any(CandidateProfile.class), eq("primary"));
        });
    }

    @Test
    void profileKeyDefaultsToPrimary_whenNotConfigured() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=DRY_RUN").run(context -> {
            CandidateProfileMigrationProperties properties = context.getBean(CandidateProfileMigrationProperties.class);
            assertThat(properties.profileKey()).isEqualTo("primary");
        });
    }

    @Configuration
    @AutoConfigurationPackage
    @EnableConfigurationProperties(CandidateProfileMigrationProperties.class)
    static class MockDependenciesConfig {

        @Bean
        CandidateProfileMigrationUseCase migrationUseCase() {
            CandidateProfileMigrationUseCase mockUseCase = mock(CandidateProfileMigrationUseCase.class);
            CandidateProfileMigrationResult dryRunResult = new CandidateProfileMigrationResult(
                    CandidateProfileMigrationMode.DRY_RUN, "primary", CandidateProfileMigrationStatus.WOULD_CREATE,
                    "fingerprint", false, false, List.of(), List.of(), 0, 0, 0, null);
            CandidateProfileMigrationResult applyResult = new CandidateProfileMigrationResult(
                    CandidateProfileMigrationMode.APPLY, "primary", CandidateProfileMigrationStatus.CREATED,
                    "fingerprint", false, false, List.of(), List.of(), 0, 0, 0, 0L);
            when(mockUseCase.dryRun(any(), any())).thenReturn(dryRunResult);
            when(mockUseCase.apply(any(), any())).thenReturn(applyResult);
            return mockUseCase;
        }

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            CandidateProfileProvider mockProvider = mock(CandidateProfileProvider.class);
            CandidatePreferences preferences = new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
            when(mockProvider.getProfile()).thenReturn(new CandidateProfile("Backend Engineer", "Senior", List.of(), List.of(), 5, preferences));
            return mockProvider;
        }
    }
}
