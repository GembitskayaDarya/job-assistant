package com.darya.jobassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobAssistantApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateProfileProvider candidateProfileProvider;

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
}
