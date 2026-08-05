package com.darya.jobassistant.candidatecontext.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.persistence.CareerHistoryRepositoryAdapter;
import com.darya.jobassistant.careerhistory.repository.CareerCompanyRepository;
import com.darya.jobassistant.careerhistory.repository.CareerHistoryRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectTechnologyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 8: proves {@link PersistentCandidateContextProvider} against real PostgreSQL -
 * Candidate Profile and Career History are read consistently within one transaction, the
 * transaction closes before the call returns (so no transaction can ever still be open across a
 * later AI call), the candidate's technical id is used to look up Career History, and a missing
 * Career History never fails the read or gets auto-created. Mirrors {@code
 * PersistentCandidateProfileProviderIntegrationTest}/{@code CareerHistoryRepositoryAdapterTest}'s
 * {@code @DataJpaTest}/Testcontainers setup, building both adapters and the provider manually
 * since none of the three is a Spring Data interface {@code @DataJpaTest}'s restricted slice scans.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class PersistentCandidateContextProviderIntegrationTest {

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

    @Autowired
    private CareerHistoryRepository careerHistoryRepository;
    @Autowired
    private CareerCompanyRepository careerCompanyRepository;
    @Autowired
    private CareerPositionRepository careerPositionRepository;
    @Autowired
    private CareerPositionResponsibilityRepository careerPositionResponsibilityRepository;
    @Autowired
    private CareerPositionAchievementRepository careerPositionAchievementRepository;
    @Autowired
    private CareerProjectRepository careerProjectRepository;
    @Autowired
    private CareerProjectResponsibilityRepository careerProjectResponsibilityRepository;
    @Autowired
    private CareerProjectAchievementRepository careerProjectAchievementRepository;
    @Autowired
    private CareerProjectTechnologyRepository careerProjectTechnologyRepository;
    @Autowired
    private EntityManager entityManager;

    private CandidateProfileRepositoryPort candidateProfileRepositoryPort() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, Clock.systemUTC());
    }

    private CareerHistoryRepositoryPort careerHistoryRepositoryPort() {
        return new CareerHistoryRepositoryAdapter(
                careerHistoryRepository, careerCompanyRepository, careerPositionRepository,
                careerPositionResponsibilityRepository, careerPositionAchievementRepository,
                careerProjectRepository, careerProjectResponsibilityRepository, careerProjectAchievementRepository,
                careerProjectTechnologyRepository, candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    private PersistentCandidateContextProvider provider(String profileKey) {
        return new PersistentCandidateContextProvider(
                candidateProfileRepositoryPort(), careerHistoryRepositoryPort(), new CandidateProfileRuntimeProperties(profileKey));
    }

    @Test
    void loadCurrentContext_existingProfileNoCareerHistory_returnsValidSnapshot() {
        String profileKey = "no-history-" + UUID.randomUUID();
        CandidateProfileAggregate saved = candidateProfileRepositoryPort().save(newProfileAggregate(profileKey));

        CandidateContextSnapshot snapshot = provider(profileKey).loadCurrentContext();

        assertThat(snapshot.candidateProfileId()).isEqualTo(saved.id());
        assertThat(snapshot.candidateProfileVersion()).isEqualTo(saved.version());
        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
    }

    @Test
    void loadCurrentContext_existingEmptyCareerHistory_returnsEmptyAvailability() {
        String profileKey = "empty-history-" + UUID.randomUUID();
        CandidateProfileAggregate saved = candidateProfileRepositoryPort().save(newProfileAggregate(profileKey));
        careerHistoryRepositoryPort().save(new CareerHistoryAggregate(null, saved.id(), List.of(), 0L));

        CandidateContextSnapshot snapshot = provider(profileKey).loadCurrentContext();

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
    }

    @Test
    void loadCurrentContext_existingPopulatedCareerHistory_returnsAvailableAvailabilityWithPreservedVersion() {
        String profileKey = "populated-history-" + UUID.randomUUID();
        CandidateProfileAggregate saved = candidateProfileRepositoryPort().save(newProfileAggregate(profileKey));
        CareerCompany company = new CareerCompany(null, "Acme Corp", null, null, null, null, 0, List.of());
        CareerHistoryAggregate savedHistory =
                careerHistoryRepositoryPort().save(new CareerHistoryAggregate(null, saved.id(), List.of(company), 0L));

        CandidateContextSnapshot snapshot = provider(profileKey).loadCurrentContext();

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
        assertThat(snapshot.careerHistory()).isPresent();
        assertThat(snapshot.careerHistory().get().version()).isEqualTo(savedHistory.version());
    }

    @Test
    void loadCurrentContext_missingCandidateProfile_throwsFocusedException() {
        String profileKey = "missing-" + UUID.randomUUID();

        assertThatThrownBy(() -> provider(profileKey).loadCurrentContext())
                .isInstanceOf(CandidateProfileNotConfiguredException.class)
                .hasMessageContaining(profileKey);
    }

    @Test
    void loadCurrentContext_correctCandidateProfileTechnicalIdIsUsedToLoadCareerHistory() {
        String profileKeyA = "profile-a-" + UUID.randomUUID();
        String profileKeyB = "profile-b-" + UUID.randomUUID();
        CandidateProfileAggregate savedA = candidateProfileRepositoryPort().save(newProfileAggregate(profileKeyA));
        CandidateProfileAggregate savedB = candidateProfileRepositoryPort().save(newProfileAggregate(profileKeyB));
        CareerCompany company = new CareerCompany(null, "Only for A", null, null, null, null, 0, List.of());
        careerHistoryRepositoryPort().save(new CareerHistoryAggregate(null, savedA.id(), List.of(company), 0L));

        CandidateContextSnapshot snapshotA = provider(profileKeyA).loadCurrentContext();
        CandidateContextSnapshot snapshotB = provider(profileKeyB).loadCurrentContext();

        assertThat(snapshotA.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
        assertThat(snapshotB.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
    }

    @Test
    void loadCurrentContext_neverCreatesCareerHistoryForAProfileThatHasNone() {
        String profileKey = "no-auto-create-" + UUID.randomUUID();
        CandidateProfileAggregate saved = candidateProfileRepositoryPort().save(newProfileAggregate(profileKey));

        provider(profileKey).loadCurrentContext();

        assertThat(careerHistoryRepositoryPort().findByCandidateProfileId(saved.id())).isEmpty();
    }

    /**
     * The smallest reliable seam for the "no transaction is held during the AI call" requirement:
     * {@link PersistentCandidateContextProvider#loadCurrentContext()} is the only
     * {@code @Transactional} method in the call path between the database read and the AI call a
     * caller (e.g. {@code AnalyzeVacancyService}) makes afterward - proving no transaction is
     * still active on this thread immediately after the call returns proves the AI call, made by
     * the caller right after, can never run inside one.
     */
    /**
     * {@code @DataJpaTest} wraps every test method in its own rollback transaction by default,
     * which would keep {@code isActualTransactionActive()} {@code true} for the whole method
     * regardless of this provider's own boundary. {@code NOT_SUPPORTED} suspends that ambient
     * test transaction for this method only, so {@link PersistentCandidateContextProvider}'s own
     * {@code @Transactional} is the only transaction in play and this assertion is meaningful.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void loadCurrentContext_noTransactionRemainsOpenAfterTheCallReturns() {
        String profileKey = "tx-boundary-" + UUID.randomUUID();
        candidateProfileRepositoryPort().save(newProfileAggregate(profileKey));

        provider(profileKey).loadCurrentContext();

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private CandidateProfileAggregate newProfileAggregate(String profileKey) {
        return new CandidateProfileAggregate(
                null, profileKey, "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), 0L);
    }
}
