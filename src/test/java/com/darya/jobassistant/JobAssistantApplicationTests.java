package com.darya.jobassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationRunner;
import com.darya.jobassistant.candidates.migration.CandidateProfileMigrationSource;
import com.darya.jobassistant.candidates.runtime.PersistentCandidateProfileProvider;
import com.darya.jobassistant.careerhistory.repository.CareerHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

class JobAssistantApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private CandidateProfileProvider candidateProfileProvider;

    @Autowired
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

    @Autowired
    private CareerHistoryRepository careerHistoryRepository;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    /**
     * Sprint 9 Step 4 requirement: with {@code candidate-profile.migration.mode} absent,
     * normal-runtime startup validation ran successfully against the seeded "primary" profile
     * (see {@code AbstractIntegrationTest}) - proven here by the context having loaded at all -
     * and {@link CandidateProfileProvider} is the PostgreSQL-backed provider, resolving to the
     * same profile {@link CandidateProfileRepositoryPort} exposes.
     */
    @Test
    void contextLoads_withPersistentCandidateProfileProviderActiveAndSeededProfileValidated() {
        assertThat(candidateProfileProvider).isInstanceOf(PersistentCandidateProfileProvider.class);

        CandidateProfile runtimeProfile = candidateProfileProvider.getProfile();
        assertThat(runtimeProfile).isNotNull();
        assertThat(runtimeProfile.targetRole()).isEqualTo("Senior Java Backend Engineer");

        assertThat(candidateProfileRepositoryPort.findByProfileKey("primary")).isPresent();
    }

    /**
     * Sprint 9 Step 4 requirement: exactly one {@link CandidateProfileProvider} bean exists in
     * the context, and {@link CandidateProfileRepositoryPort} (persistence access) remains a
     * distinct abstraction it depends on - not an ambiguous second provider.
     */
    @Test
    void contextLoads_withExactlyOneCandidateProfileProviderBean() {
        assertThat(applicationContext.getBeansOfType(CandidateProfileProvider.class)).hasSize(1);
        assertThat(candidateProfileRepositoryPort).isNotNull();
        assertThat(candidateProfileRepositoryPort).isNotSameAs(candidateProfileProvider);
    }

    /**
     * Sprint 9 Step 4 requirement: with {@code candidate-profile.migration.mode} absent from both
     * {@code application.yml} and the test configuration, neither {@link
     * CandidateProfileMigrationRunner} nor the YAML-backed {@link CandidateProfileMigrationSource}
     * is even constructed - proving, against the real application context, that normal startup
     * performs no migration read or write and never touches {@code candidate-profile.yml}.
     */
    @Test
    void contextLoads_withMigrationPropertyAbsent_migrationBeansDoNotExist() {
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> applicationContext.getBean(CandidateProfileMigrationRunner.class));
        assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                .isThrownBy(() -> applicationContext.getBean(CandidateProfileMigrationSource.class));
    }

    /**
     * Sprint 9 Step 5 requirements 36-39, folded in here rather than kept as a separate {@code
     * CareerHistoryRuntimeCompatibilityTest} (Step 5 correction): that class had byte-for-byte
     * identical Spring configuration to this one, so it added a second, entirely redundant full
     * application context/container lifecycle for no additional coverage - this class already
     * proves the context starts normally with a valid, seeded Candidate Profile (see {@code
     * AbstractIntegrationTest}), which is exactly the "starts normally with Career History absent"
     * condition, since nothing here seeds or requires one.
     */
    @Test
    void contextLoads_withCareerHistoryAbsent_noRowIsAutomaticallyCreated() {
        assertThat(careerHistoryRepository.findAll()).isEmpty();
    }
}
