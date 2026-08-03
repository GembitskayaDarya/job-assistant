package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 4: proves {@link CandidateProfileStartupValidator}'s activation rule without a
 * real database - enabled whenever {@code candidate-profile.migration.mode} is absent or {@code
 * OFF} (normal runtime), disabled whenever it is {@code DRY_RUN}/{@code APPLY} (migration must be
 * able to run against a database that does not have the profile yet).
 */
class CandidateProfileStartupValidatorActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockDependenciesConfig.class, CandidateProfileStartupValidator.class);

    @Test
    void migrationPropertyAbsent_validatorBeanExists() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(CandidateProfileStartupValidator.class));
    }

    @Test
    void modeOff_validatorBeanExists() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=OFF")
                .run(context -> assertThat(context).hasSingleBean(CandidateProfileStartupValidator.class));
    }

    @Test
    void modeDryRun_validatorBeanDoesNotExist() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=DRY_RUN")
                .run(context -> assertThat(context).doesNotHaveBean(CandidateProfileStartupValidator.class));
    }

    @Test
    void modeApply_validatorBeanDoesNotExist() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=APPLY")
                .run(context -> assertThat(context).doesNotHaveBean(CandidateProfileStartupValidator.class));
    }

    @Configuration
    @EnableConfigurationProperties(CandidateProfileRuntimeProperties.class)
    static class MockDependenciesConfig {

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            CandidateProfileProvider mockProvider = mock(CandidateProfileProvider.class);
            CandidatePreferences preferences = new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
            when(mockProvider.getProfile()).thenReturn(new CandidateProfile("Backend Engineer", "Senior", List.of(), List.of(), 5, preferences));
            return mockProvider;
        }
    }
}
