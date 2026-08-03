package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.config.CandidateProfileProperties;
import com.darya.jobassistant.candidates.config.YamlCandidateProfileMigrationSource;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationProperties;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunner;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationUseCase;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.candidates.runtime.CandidateProfileStartupValidator;
import com.darya.jobassistant.candidates.runtime.PersistentCandidateProfileProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 4 correction: proves the exact bean graph for every {@code
 * candidate-profile.migration.mode} state, all four beans together, without a real database -
 * {@link ApplicationContextRunner} with mocked {@link CandidateProfileRepositoryPort}/{@link
 * CandidateProfileMigrationUseCase}.
 *
 * <pre>
 * property absent / OFF:  PersistentCandidateProfileProvider present, CandidateProfileStartupValidator present,
 *                          YamlCandidateProfileMigrationSource absent, CandidateProfileMigrationRunner absent
 * DRY_RUN / APPLY:        PersistentCandidateProfileProvider present, CandidateProfileStartupValidator absent,
 *                          YamlCandidateProfileMigrationSource present, CandidateProfileMigrationRunner present
 * invalid value:          context fails to start (enum binding failure), no bean assertions apply
 * </pre>
 */
class CandidateProfileBeanActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    MockDependenciesConfig.class,
                    PersistentCandidateProfileProvider.class,
                    CandidateProfileStartupValidator.class,
                    YamlCandidateProfileMigrationSource.class,
                    CandidateProfileMigrationRunner.class);

    @Test
    void migrationPropertyAbsent_normalRuntimeBeansOnly() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertNormalRuntimeBeanGraph(context);
        });
    }

    @Test
    void modeOff_normalRuntimeBeansOnly() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=OFF").run(context -> {
            assertThat(context).hasNotFailed();
            assertNormalRuntimeBeanGraph(context);
        });
    }

    @Test
    void modeDryRun_migrationBeansOnly() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=DRY_RUN").run(context -> {
            assertThat(context).hasNotFailed();
            assertMigrationBeanGraph(context);
        });
    }

    @Test
    void modeApply_migrationBeansOnly() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=APPLY").run(context -> {
            assertThat(context).hasNotFailed();
            assertMigrationBeanGraph(context);
        });
    }

    @Test
    void invalidModeValue_failsConfigurationBindingClearly() {
        contextRunner.withPropertyValues("candidate-profile.migration.mode=BOGUS")
                .run(context -> assertThat(context).hasFailed());
    }

    private void assertNormalRuntimeBeanGraph(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(PersistentCandidateProfileProvider.class);
        assertThat(context.getBean(CandidateProfileProvider.class)).isExactlyInstanceOf(PersistentCandidateProfileProvider.class);
        assertThat(context).hasSingleBean(CandidateProfileStartupValidator.class);
        assertThat(context).doesNotHaveBean(YamlCandidateProfileMigrationSource.class);
        assertThat(context).doesNotHaveBean(CandidateProfileMigrationRunner.class);
    }

    private void assertMigrationBeanGraph(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(PersistentCandidateProfileProvider.class);
        assertThat(context.getBean(CandidateProfileProvider.class)).isExactlyInstanceOf(PersistentCandidateProfileProvider.class);
        assertThat(context).doesNotHaveBean(CandidateProfileStartupValidator.class);
        assertThat(context).hasSingleBean(YamlCandidateProfileMigrationSource.class);
        assertThat(context).hasSingleBean(CandidateProfileMigrationRunner.class);
    }

    @Configuration
    @AutoConfigurationPackage
    @EnableConfigurationProperties({
            CandidateProfileRuntimeProperties.class,
            CandidateProfileProperties.class,
            CandidateProfileMigrationProperties.class
    })
    static class MockDependenciesConfig {

        @Bean
        CandidateProfileRepositoryPort candidateProfileRepositoryPort() {
            CandidateProfileRepositoryPort mockPort = mock(CandidateProfileRepositoryPort.class);
            when(mockPort.findByProfileKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
            return mockPort;
        }

        @Bean
        CandidateProfileMigrationUseCase candidateProfileMigrationUseCase() {
            return mock(CandidateProfileMigrationUseCase.class);
        }
    }
}
