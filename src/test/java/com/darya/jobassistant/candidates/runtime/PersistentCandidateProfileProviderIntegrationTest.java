package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 4: proves {@link PersistentCandidateProfileProvider} against real PostgreSQL -
 * every call performs a fresh read (no caching), so a committed update through {@link
 * CandidateProfileRepositoryPort#save} is visible on the very next {@code getProfile()} call
 * without any restart or explicit refresh, and a profile removed after startup makes the next
 * call fail rather than fall back to anything. Mirrors {@code CandidateProfileRepositoryAdapterTest}'s
 * {@code @DataJpaTest}/Testcontainers setup, building both the adapter and the provider manually
 * since neither is a Spring Data interface {@code @DataJpaTest}'s restricted slice scans.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class PersistentCandidateProfileProviderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CandidateProfileSkillRepository candidateProfileSkillRepository;

    @Autowired
    private CandidateProfileLanguageRepository candidateProfileLanguageRepository;

    @Autowired
    private CandidateProfilePreferenceRepository candidateProfilePreferenceRepository;

    private CandidateProfileRepositoryPort repositoryPort() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, Clock.systemUTC());
    }

    private PersistentCandidateProfileProvider provider(String profileKey) {
        return new PersistentCandidateProfileProvider(repositoryPort(), new CandidateProfileRuntimeProperties(profileKey));
    }

    @Test
    void getProfile_existingAggregate_assemblesToExpectedAnalysisProfile() {
        String profileKey = "existing-" + UUID.randomUUID();
        repositoryPort().save(newAggregate(profileKey, "Senior Java Backend Engineer", 6));

        CandidateProfile profile = provider(profileKey).getProfile();

        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.experienceYears()).isEqualTo(6);
    }

    @Test
    void getProfile_missingProfileKey_throwsCandidateProfileNotConfiguredExceptionWithRequestedKey() {
        String profileKey = "missing-" + UUID.randomUUID();

        assertThatThrownBy(() -> provider(profileKey).getProfile())
                .isInstanceOf(CandidateProfileNotConfiguredException.class)
                .hasMessageContaining(profileKey);
    }

    /**
     * Sprint 9 Step 4 requirement 9: proves runtime refresh semantics end-to-end - no restart or
     * explicit cache invalidation is needed for a committed update to become visible.
     */
    @Test
    void getProfile_afterCommittedUpdate_reflectsTheLatestVersionOnTheNextCall() {
        String profileKey = "refresh-" + UUID.randomUUID();
        CandidateProfileAggregate saved = repositoryPort().save(newAggregate(profileKey, "Backend Engineer", 4));
        PersistentCandidateProfileProvider provider = provider(profileKey);

        CandidateProfile before = provider.getProfile();
        assertThat(before.targetRole()).isEqualTo("Backend Engineer");
        assertThat(before.experienceYears()).isEqualTo(4);

        repositoryPort().save(new CandidateProfileAggregate(
                saved.id(), profileKey, "Staff Backend Engineer", "Staff", 7,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), saved.version()));

        CandidateProfile after = provider.getProfile();
        assertThat(after.targetRole()).isEqualTo("Staff Backend Engineer");
        assertThat(after.experienceYears()).isEqualTo(7);
    }

    @Test
    void getProfile_deletedAfterStartup_failsRatherThanFallingBack() {
        String profileKey = "deleted-" + UUID.randomUUID();
        CandidateProfileAggregate saved = repositoryPort().save(newAggregate(profileKey, "Backend Engineer", 4));
        PersistentCandidateProfileProvider provider = provider(profileKey);
        assertThat(provider.getProfile()).isNotNull();

        candidateProfileRepository.deleteById(saved.id());

        assertThatThrownBy(provider::getProfile).isInstanceOf(CandidateProfileNotConfiguredException.class);
    }

    private CandidateProfileAggregate newAggregate(String profileKey, String targetRole, int experienceYears) {
        return new CandidateProfileAggregate(
                null, profileKey, targetRole, "Senior", experienceYears,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), 0L);
    }
}
