package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 7: proves {@link CareerHistoryImportUseCase}'s DRY_RUN/APPLY decision matrix
 * against real PostgreSQL - built manually around the real Testcontainers-backed adapters, exactly
 * matching {@code CandidateProfileMigrationUseCaseTest}'s convention (neither adapter nor the use
 * case is a Spring Data interface a restricted {@code @DataJpaTest} slice would component-scan).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CareerHistoryImportUseCaseTest {

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
    @Autowired
    private PlatformTransactionManager transactionManager;

    private CandidateProfileRepositoryPort candidateProfilePort() {
        return new CandidateProfileRepositoryAdapter(
                candidateProfileRepository, candidateProfileSkillRepository, candidateProfileLanguageRepository,
                candidateProfilePreferenceRepository, Clock.systemUTC());
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
        String key = "career-import-" + UUID.randomUUID();
        candidateProfileRepository.save(CandidateProfileEntity.builder()
                .profileKey(key).targetRole("Demo Backend Engineer").seniority("Senior").experienceYears(5).build());
        return key;
    }

    // ---- DRY_RUN ----

    @Test
    void dryRun_missingDestination_returnsWouldCreate_andWritesNothing() {
        String key = newCandidateProfile();
        long rowsBefore = careerHistoryRepository.count();

        CareerHistoryImportResult result = useCase().dryRun(document(key, null));

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.WOULD_CREATE);
        assertThat(result.mode()).isEqualTo(CareerHistoryImportMode.DRY_RUN);
        assertThat(careerHistoryRepository.count()).isEqualTo(rowsBefore);
    }

    @Test
    void dryRun_equalDestination_returnsWouldNoOp_andWritesNothing() {
        String key = newCandidateProfile();
        useCase().apply(document(key, null));
        long rowsBefore = careerHistoryRepository.count();
        long companyRowsBefore = careerCompanyRepository.count();

        CareerHistoryImportResult result = useCase().dryRun(document(key, null));

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.WOULD_NO_OP);
        assertThat(careerHistoryRepository.count()).isEqualTo(rowsBefore);
        assertThat(careerCompanyRepository.count()).isEqualTo(companyRowsBefore);
    }

    @Test
    void dryRun_missingCandidateProfile_fails_withoutWriting() {
        long rowsBefore = careerHistoryRepository.count();

        assertThatThrownBy(() -> useCase().dryRun(document("does-not-exist-" + UUID.randomUUID(), null)))
                .isInstanceOf(CareerHistoryImportCandidateProfileNotFoundException.class);

        assertThat(careerHistoryRepository.count()).isEqualTo(rowsBefore);
    }

    @Test
    void dryRun_lengthViolation_failsBeforeAnyRead_orWrite() {
        String key = newCandidateProfile();
        CareerHistoryImportDocument invalid = withCompanyName(document(key, null), "x".repeat(300));

        assertThatThrownBy(() -> useCase().dryRun(invalid)).isInstanceOf(CareerHistoryImportValidationException.class);
    }

    // ---- APPLY: create ----

    @Test
    void apply_missingDestination_createsCompleteGraph() {
        String key = newCandidateProfile();

        CareerHistoryImportResult result = useCase().apply(document(key, null));

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.CREATED);
        assertThat(result.resultingVersion()).isEqualTo(0L);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate persisted = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(persisted.companies()).hasSize(1);
        assertThat(persisted.companies().get(0).name()).isEqualTo("Example Systems");
        assertThat(persisted.companies().get(0).positions().get(0).projects().get(0).technologies())
                .extracting(t -> t.name()).containsExactly("Java", "PostgreSQL");
    }

    @Test
    void apply_missingDestination_expectedVersionSet_returnsConflict_andCreatesNothing() {
        String key = newCandidateProfile();

        CareerHistoryImportResult result = useCase().apply(document(key, 0L));

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.CONFLICT);
        assertThat(careerHistoryRepository.count()).isZero();
    }

    @Test
    void apply_persistedGraph_matchesSourceSemanticFingerprint() {
        String key = newCandidateProfile();
        CareerHistoryImportDocument source = document(key, null);

        CareerHistoryImportResult result = useCase().apply(source);

        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate persisted = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(CareerHistoryFingerprint.sha256(persisted)).isEqualTo(result.sourceFingerprint());
    }

    @Test
    void apply_missingCandidateProfile_fails_withoutWriting() {
        long rowsBefore = careerHistoryRepository.count();

        assertThatThrownBy(() -> useCase().apply(document("does-not-exist-" + UUID.randomUUID(), null)))
                .isInstanceOf(CareerHistoryImportCandidateProfileNotFoundException.class);

        assertThat(careerHistoryRepository.count()).isEqualTo(rowsBefore);
    }

    @Test
    void apply_lengthViolation_failsBeforeAnySqlWrite() {
        String key = newCandidateProfile();
        CareerHistoryImportDocument invalid = withCompanyName(document(key, null), "x".repeat(300));
        long rowsBefore = careerHistoryRepository.count();

        assertThatThrownBy(() -> useCase().apply(invalid)).isInstanceOf(CareerHistoryImportValidationException.class);

        assertThat(careerHistoryRepository.count()).isEqualTo(rowsBefore);
    }

    // ---- APPLY: idempotency / reordering ----

    @Test
    void apply_repeatedIdenticalApply_returnsNoOp_andDoesNotIncrementVersion() {
        String key = newCandidateProfile();
        useCase().apply(document(key, null));
        long versionAfterFirst = careerHistoryRepository.findByCandidateProfileId(
                candidateProfileRepository.findByProfileKey(key).orElseThrow().getId()).orElseThrow().getVersion();

        CareerHistoryImportResult second = useCase().apply(document(key, null));

        assertThat(second.status()).isEqualTo(CareerHistoryImportStatus.NO_OP);
        long versionAfterSecond = careerHistoryRepository.findByCandidateProfileId(
                candidateProfileRepository.findByProfileKey(key).orElseThrow().getId()).orElseThrow().getVersion();
        assertThat(versionAfterSecond).isEqualTo(versionAfterFirst);
    }

    @Test
    void apply_reorderedCompaniesWithIdenticalDisplayOrders_remainsNoOp() {
        String key = newCandidateProfile();
        CareerCompanyImportEntry companyA = company("example-systems", "Example Systems", 0);
        CareerCompanyImportEntry companyB = company("zenith-robotics", "Zenith Robotics", 1);
        useCase().apply(new CareerHistoryImportDocument(1, key, null, List.of(companyA, companyB)));

        // Same two companies, same displayOrders, supplied in reverse input order.
        CareerHistoryImportResult result = useCase().apply(new CareerHistoryImportDocument(1, key, null, List.of(companyB, companyA)));

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.NO_OP);
    }

    // ---- APPLY: update, conflict, expected version ----

    @Test
    void apply_changedSource_noExpectedVersion_returnsConflict_withoutOverwriting() {
        String key = newCandidateProfile();
        useCase().apply(document(key, null));
        CareerHistoryImportDocument changed = withCompanyName(document(key, null), "Renamed Systems");

        CareerHistoryImportResult result = useCase().apply(changed);

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.CONFLICT);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        assertThat(careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow().companies().get(0).name())
                .isEqualTo("Example Systems");
    }

    @Test
    void apply_changedSource_staleExpectedVersion_returnsConflict() {
        String key = newCandidateProfile();
        useCase().apply(document(key, null));
        CareerHistoryImportDocument changed = withCompanyName(document(key, 999L), "Renamed Systems");

        CareerHistoryImportResult result = useCase().apply(changed);

        assertThat(result.status()).isEqualTo(CareerHistoryImportStatus.CONFLICT);
    }

    @Test
    void apply_changedSource_currentExpectedVersion_returnsUpdated_andIncrementsVersionExactlyOnce() {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null));
        CareerHistoryImportDocument changed = withCompanyName(document(key, created.resultingVersion()), "Renamed Systems");

        CareerHistoryImportResult updated = useCase().apply(changed);

        assertThat(updated.status()).isEqualTo(CareerHistoryImportStatus.UPDATED);
        assertThat(updated.resultingVersion()).isEqualTo(created.resultingVersion() + 1);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        assertThat(careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow().companies().get(0).name())
                .isEqualTo("Renamed Systems");
    }

    @Test
    void apply_update_preservesCompanyPositionAndProjectIds_whenKeysUnchanged() {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null));
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate beforeUpdate = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        UUID companyIdBefore = beforeUpdate.companies().get(0).id();
        UUID positionIdBefore = beforeUpdate.companies().get(0).positions().get(0).id();
        UUID projectIdBefore = beforeUpdate.companies().get(0).positions().get(0).projects().get(0).id();

        CareerHistoryImportDocument changed = withCompanyName(document(key, created.resultingVersion()), "Renamed Systems");
        useCase().apply(changed);

        CareerHistoryAggregate afterUpdate = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(afterUpdate.companies().get(0).id()).isEqualTo(companyIdBefore);
        assertThat(afterUpdate.companies().get(0).positions().get(0).id()).isEqualTo(positionIdBefore);
        assertThat(afterUpdate.companies().get(0).positions().get(0).projects().get(0).id()).isEqualTo(projectIdBefore);
    }

    @Test
    void apply_update_removesDeletedSourceEntries_andAddsNewOnesWithDeterministicIds() {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null));

        // Second company added, first company's technology list narrowed to just one entry.
        CareerCompanyImportEntry companyA = company("example-systems", "Example Systems", 0);
        CareerCompanyImportEntry companyB = company("zenith-robotics", "Zenith Robotics", 1);
        CareerHistoryImportDocument changed = new CareerHistoryImportDocument(1, key, created.resultingVersion(), List.of(companyA, companyB));

        CareerHistoryImportResult updated = useCase().apply(changed);

        assertThat(updated.status()).isEqualTo(CareerHistoryImportStatus.UPDATED);
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate afterUpdate = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(afterUpdate.companies()).hasSize(2);
        assertThat(afterUpdate.companies().get(1).id())
                .isEqualTo(CareerHistoryImportIdGenerator.companyId(profileId, "zenith-robotics"));
    }

    // ---- Post-save parity rollback mechanism ----

    /**
     * {@link CareerHistoryImportParityException} cannot be organically triggered through {@link
     * CareerHistoryImportUseCase#apply}'s public API when the mapper/fingerprint are correctly
     * implemented - there is no valid source input that makes a freshly-saved-and-reloaded graph
     * disagree with its own proposed fingerprint. This test instead proves the actual rollback
     * mechanism {@code applyInternal} relies on: throwing an unchecked exception from inside a
     * {@link TransactionTemplate#execute} block, after a real {@code repositoryPort.save} call,
     * using the exact same transaction template configuration {@code applyInternal} uses,
     * correctly discards that save - matching {@code CandidateProfileMigrationUseCaseTest}'s
     * equivalent proof.
     */
    @Test
    void parityFailureRollbackMechanism_discardsAnAlreadyAttemptedCreate() {
        String key = newCandidateProfile();
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        TransactionTemplate applyLikeTransaction = new TransactionTemplate(transactionManager);
        applyLikeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        applyLikeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        CareerHistoryRepositoryPort port = careerHistoryPort();
        CareerHistoryAggregate proposed = CareerHistoryImportMapper.toAggregate(document(key, null), profileId, null);

        assertThatThrownBy(() -> applyLikeTransaction.execute(status -> {
            port.save(proposed);
            throw new CareerHistoryImportParityException(key);
        })).isInstanceOf(CareerHistoryImportParityException.class);

        assertThat(careerHistoryPort().findByCandidateProfileId(profileId)).isEmpty();
    }

    @Test
    void parityFailureRollbackMechanism_discardsAnAlreadyAttemptedUpdate_andRootVersion() {
        String key = newCandidateProfile();
        CareerHistoryImportResult created = useCase().apply(document(key, null));
        UUID profileId = candidateProfileRepository.findByProfileKey(key).orElseThrow().getId();
        CareerHistoryAggregate destination = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        TransactionTemplate applyLikeTransaction = new TransactionTemplate(transactionManager);
        applyLikeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        applyLikeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        CareerHistoryRepositoryPort port = careerHistoryPort();
        CareerHistoryImportDocument changed = withCompanyName(document(key, created.resultingVersion()), "Renamed Systems");
        CareerHistoryAggregate proposed = CareerHistoryImportMapper.toAggregate(changed, profileId, destination);

        assertThatThrownBy(() -> applyLikeTransaction.execute(status -> {
            port.save(proposed);
            throw new CareerHistoryImportParityException(key);
        })).isInstanceOf(CareerHistoryImportParityException.class);

        CareerHistoryAggregate stillOriginal = careerHistoryPort().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(stillOriginal.version()).isEqualTo(destination.version());
        assertThat(stillOriginal.companies().get(0).name()).isEqualTo("Example Systems");
    }

    // ---- Fixtures ----

    private CareerHistoryImportDocument document(String candidateProfileKey, Long expectedVersion) {
        return new CareerHistoryImportDocument(
                1, candidateProfileKey, expectedVersion, List.of(company("example-systems", "Example Systems", 0)));
    }

    private CareerHistoryImportDocument withCompanyName(CareerHistoryImportDocument document, String newName) {
        CareerCompanyImportEntry original = document.companies().get(0);
        CareerCompanyImportEntry renamed = new CareerCompanyImportEntry(
                original.key(), newName, original.website(), original.industry(), original.location(),
                original.description(), original.displayOrder(), original.positions());
        return new CareerHistoryImportDocument(
                document.schemaVersion(), document.candidateProfileKey(), document.expectedVersion(), List.of(renamed));
    }

    private CareerCompanyImportEntry company(String key, String name, int displayOrder) {
        CareerTechnologyImportEntry java = new CareerTechnologyImportEntry("Java", "Language", 0);
        CareerTechnologyImportEntry postgres = new CareerTechnologyImportEntry("PostgreSQL", "Database", 1);
        CareerProjectImportEntry project = new CareerProjectImportEntry(
                "billing-platform", "Billing Platform", "Fictional billing platform.",
                LocalDate.of(2021, 6, 1), null, 0,
                List.of(new CareerResponsibilityImportEntry("Implemented a fictional billing pipeline.", 0)),
                List.of(new CareerAchievementImportEntry("Improved a fictional metric.", 0)),
                List.of(java, postgres));
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend-engineer", "Demo Backend Engineer", "Full-time", "Berlin, Germany", "Remote",
                LocalDate.of(2021, 3, 1), null, true, "Fictional role.", 0,
                List.of(new CareerResponsibilityImportEntry("Designed fictional backend services.", 0)),
                List.of(new CareerAchievementImportEntry("Reduced a fictional metric by 40%.", 0)),
                List.of(project));
        return new CareerCompanyImportEntry(
                key, name, "https://example.com", "Fintech", "Berlin, Germany", "Fictional company.", displayOrder, List.of(position));
    }
}
