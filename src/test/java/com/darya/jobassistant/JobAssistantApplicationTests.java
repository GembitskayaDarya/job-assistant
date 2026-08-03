package com.darya.jobassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunner;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

class JobAssistantApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateProfileProvider candidateProfileProvider;

    @Autowired
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    /**
     * Sprint 9 Step 1 requirement: the application must start successfully once V16 has run, with
     * {@code candidate_profile} (and its skill/language tables) present but empty - no profile is
     * seeded anywhere, and {@code ConfigurationCandidateProfileProvider} (backed by {@code
     * candidate-profile-test.yml}, see build.gradle's {@code CANDIDATE_PROFILE_PATH} override)
     * remains the only source of the runtime {@link CandidateProfile} bean.
     */
    @Test
    void contextLoads_withEmptyCandidateProfileTablesAndYamlProviderStillActive() {
        assertThat(candidateProfileRepository.findAll()).isEmpty();

        CandidateProfile runtimeProfile = candidateProfileProvider.getProfile();
        assertThat(runtimeProfile).isNotNull();
        assertThat(runtimeProfile.targetRole()).isEqualTo("Senior Java Backend Engineer");
    }

    /**
     * Sprint 9 Step 2 requirement: {@link CandidateProfileRepositoryPort} (persistence access) and
     * {@link CandidateProfileProvider} (the source AI vacancy analysis actually reads from) are
     * different abstractions that both exist in the context without ambiguous injection or one
     * displacing the other - registering the new adapter bean must not change which provider
     * {@code JobAnalysisService} resolves.
     */
    @Test
    void contextLoads_withBothCandidateProfileProviderAndRepositoryPortRegisteredUnambiguously() {
        assertThat(candidateProfileRepositoryPort).isNotNull();
        assertThat(candidateProfileProvider).isNotNull();
        assertThat(candidateProfileRepositoryPort).isNotSameAs(candidateProfileProvider);
    }

    /**
     * Sprint 9 Step 3 requirement: with {@code candidate-profile.migration.mode} absent from both
     * {@code application.yml} and the test configuration, {@link CandidateProfileMigrationRunner}
     * must not even be constructed - proving, against the real application context (not just the
     * isolated {@code ApplicationContextRunner} unit test), that normal startup performs no
     * migration read or write and {@code ConfigurationCandidateProfileProvider} remains the only
     * active Candidate Profile source.
     */
    @Test
    void contextLoads_withMigrationPropertyAbsent_migrationRunnerBeanDoesNotExist() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> applicationContext.getBean(CandidateProfileMigrationRunner.class));
    }
}
