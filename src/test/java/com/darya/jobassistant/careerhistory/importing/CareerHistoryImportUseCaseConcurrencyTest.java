package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileEducationRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.importing.source.CareerAchievementImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerCompanyImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.careerhistory.importing.source.CareerPositionImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerProjectImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerResponsibilityImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerTechnologyImportEntry;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 7 concurrency scenarios for {@link CareerHistoryImportUseCase#apply} - mirrors
 * {@code CandidateProfileMigrationUseCaseTest}'s concurrency section exactly, against real
 * PostgreSQL via the real adapters.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CareerHistoryImportUseCaseConcurrencyTest {

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
    private CandidateProfileEducationRepository candidateProfileEducationRepository;

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
    @Autowired
    private PlatformTransactionManager transactionManager;

    private CandidateProfileRepositoryPort candidateProfilePort() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, candidateProfileEducationRepository, Clock.systemUTC());
    }

    private CareerHistoryRepositoryPort careerHistoryPort() {
        return new CareerHistoryRepositoryAdapter(
                careerHistoryRepository, careerCompanyRepository, careerPositionRepository,
                careerPositionResponsibilityRepository, careerPositionAchievementRepository,
                careerProjectRepository, careerProjectResponsibilityRepository, careerProjectAchievementRepository,
                careerProjectTechnologyRepository, candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    private CareerHistoryImportUseCase useCase() {
        return new CareerHistoryImportUseCase(candidateProfilePort(), careerHistoryPort(), transactionManager);
    }

    private String newCandidateProfile() {
        String key = "career-concurrency-" + UUID.randomUUID();
        candidateProfileRepository.save(CandidateProfileEntity.builder()
                .profileKey(key).targetRole("Demo Backend Engineer").seniority("Senior").experienceYears(5).build());
        return key;
    }

    @Test
    void concurrentApply_sameNewCandidateProfile_sameSource_resolvesToExactlyOneCreatedAndOneNoOp() throws Exception {
        String key = newCandidateProfile();
        CareerHistoryImportDocument source = document(key, null, "Example Systems");
        List<CareerHistoryImportStatus> statuses = runConcurrently(() -> useCase().apply(source), () -> useCase().apply(source));

        assertThat(statuses).containsExactlyInAnyOrder(CareerHistoryImportStatus.CREATED, CareerHistoryImportStatus.NO_OP);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        assertThat(careerHistoryRepository.findByCandidateProfileId(profileId)).isPresent();
        assertThat(careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow().companies().get(0).name())
                .isEqualTo("Example Systems");
    }

    @Test
    void concurrentApply_sameNewCandidateProfile_differentSources_resolvesToExactlyOneCreatedAndOneConflict() throws Exception {
        String key = newCandidateProfile();
        CareerHistoryImportDocument sourceA = document(key, null, "Example Systems");
        CareerHistoryImportDocument sourceB = document(key, null, "Zenith Robotics");
        List<CareerHistoryImportStatus> statuses = runConcurrently(() -> useCase().apply(sourceA), () -> useCase().apply(sourceB));

        assertThat(statuses).containsExactlyInAnyOrder(CareerHistoryImportStatus.CREATED, CareerHistoryImportStatus.CONFLICT);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        assertThat(careerHistoryRepository.findByCandidateProfileId(profileId)).isPresent();
        assertThat(careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow().companies().get(0).name())
                .isIn("Example Systems", "Zenith Robotics");
    }

    @Test
    void concurrentApply_sameUpdate_sameExpectedVersion_identicalSource_resolvesToExactlyOneUpdatedAndOneNoOp() throws Exception {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null, "Example Systems"));
        CareerHistoryImportDocument changed = document(key, created.resultingVersion(), "Renamed Systems");

        List<CareerHistoryImportStatus> statuses = runConcurrently(() -> useCase().apply(changed), () -> useCase().apply(changed));

        assertThat(statuses).containsExactlyInAnyOrder(CareerHistoryImportStatus.UPDATED, CareerHistoryImportStatus.NO_OP);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate finalState = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(finalState.companies().get(0).name()).isEqualTo("Renamed Systems");
        assertThat(finalState.version()).isEqualTo(created.resultingVersion() + 1);
    }

    @Test
    void concurrentApply_sameUpdate_sameExpectedVersion_differentSources_resolvesToExactlyOneUpdatedAndOneConflict() throws Exception {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null, "Example Systems"));
        CareerHistoryImportDocument changedA = document(key, created.resultingVersion(), "Renamed Alpha");
        CareerHistoryImportDocument changedB = document(key, created.resultingVersion(), "Renamed Beta");

        List<CareerHistoryImportStatus> statuses = runConcurrently(() -> useCase().apply(changedA), () -> useCase().apply(changedB));

        assertThat(statuses).containsExactlyInAnyOrder(CareerHistoryImportStatus.UPDATED, CareerHistoryImportStatus.CONFLICT);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate finalState = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(finalState.companies().get(0).name()).isIn("Renamed Alpha", "Renamed Beta");
        assertThat(finalState.version()).isEqualTo(created.resultingVersion() + 1);
    }

    @FunctionalInterface
    private interface ResultSupplier {
        CareerHistoryImportResult get() throws Exception;
    }

    private List<CareerHistoryImportStatus> runConcurrently(ResultSupplier first, ResultSupplier second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<CareerHistoryImportResult>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                barrier.await();
                return first.get();
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return second.get();
            }));
            List<CareerHistoryImportStatus> statuses = new ArrayList<>();
            for (Future<CareerHistoryImportResult> future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS).status());
            }
            return statuses;
        } finally {
            executor.shutdown();
        }
    }

    private CareerHistoryImportDocument document(String candidateProfileKey, Long expectedVersion, String companyName) {
        CareerTechnologyImportEntry postgres = new CareerTechnologyImportEntry("PostgreSQL", "Database", 0);
        CareerProjectImportEntry project = new CareerProjectImportEntry(
                "billing-platform", "Billing Platform", null, LocalDate.of(2021, 6, 1), null, 0,
                List.of(new CareerResponsibilityImportEntry("Implemented a fictional pipeline.", 0)),
                List.of(new CareerAchievementImportEntry("Improved a fictional metric.", 0)), List.of(postgres));
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend-engineer", "Demo Backend Engineer", null, null, null,
                LocalDate.of(2021, 3, 1), null, true, null, 0, List.of(), List.of(), List.of(project));
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", companyName, null, null, null, null, 0, List.of(position));
        return new CareerHistoryImportDocument(1, candidateProfileKey, expectedVersion, List.of(company));
    }
}
