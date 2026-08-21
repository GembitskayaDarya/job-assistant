package com.darya.jobassistant.applicationmaterials.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 10 Step 1: proves the V21 {@code application_material_generation} persistence
 * foundation's database invariants against real PostgreSQL - mirrors {@code
 * CareerHistoryRepositoryTest}'s structure and conventions: one {@code @DataJpaTest} covering
 * persistence, ordering, every CHECK constraint, cascade, and optimistic locking, each
 * CHECK-violation case in its own test method (a CHECK violation aborts the whole PostgreSQL
 * transaction).
 *
 * <p>All test data is fictional: no real employer, vacancy, or content appears anywhere in this
 * file.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class ApplicationMaterialGenerationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationMaterialGenerationRepository generationRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant STARTED_AT = REQUESTED_AT.plusSeconds(10);
    private static final Instant COMPLETED_AT = STARTED_AT.plusSeconds(20);

    // ==================== 1-3. Valid persistence ====================

    @Test
    void generation_persistedForAnExistingVacancy() {
        Vacancy vacancy = aVacancy("persist-" + UUID.randomUUID());

        ApplicationMaterialGenerationEntity saved = generationRepository.save(pending(vacancy));
        entityManager.flush();
        entityManager.clear();

        ApplicationMaterialGenerationEntity reloaded = generationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVacancy().getId()).isEqualTo(vacancy.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void multipleGenerations_canBePersistedForOneVacancy() {
        // Distinct source fingerprints - three simultaneously PENDING generations for the same
        // vacancy and the same fingerprint would collide with V31's active-uniqueness index (see
        // ApplicationMaterialGenerationRepositoryAdapterTest's "V31 active-uniqueness" section);
        // this test is about persisting/listing several rows, not about that invariant.
        Vacancy vacancy = aVacancy("multi-gen-" + UUID.randomUUID());

        generationRepository.save(pendingWithSourceFingerprint(vacancy, REQUESTED_AT, "a".repeat(64)));
        generationRepository.save(pendingWithSourceFingerprint(vacancy, REQUESTED_AT, "b".repeat(64)));
        generationRepository.save(pendingWithSourceFingerprint(vacancy, REQUESTED_AT, "c".repeat(64)));
        entityManager.flush();
        entityManager.clear();

        assertThat(generationRepository.findAllByVacancyIdOrderByRequestedAtDesc(vacancy.getId())).hasSize(3);
    }

    @Test
    void generations_returnedNewestRequestedFirst() {
        // Distinct source fingerprints - see multipleGenerations_canBePersistedForOneVacancy.
        Vacancy vacancy = aVacancy("ordering-" + UUID.randomUUID());

        ApplicationMaterialGenerationEntity oldest = generationRepository.save(
                pendingWithSourceFingerprint(vacancy, REQUESTED_AT, "a".repeat(64)));
        ApplicationMaterialGenerationEntity newest = generationRepository.save(
                pendingWithSourceFingerprint(vacancy, REQUESTED_AT.plus(1, ChronoUnit.DAYS), "b".repeat(64)));
        entityManager.flush();
        entityManager.clear();

        List<ApplicationMaterialGenerationEntity> generations =
                generationRepository.findAllByVacancyIdOrderByRequestedAtDesc(vacancy.getId());
        assertThat(generations).extracting(ApplicationMaterialGenerationEntity::getId)
                .containsExactly(newest.getId(), oldest.getId());
    }

    // ==================== 4. FK enforcement ====================

    @Test
    void generation_withoutVacancy_isRejected() {
        ApplicationMaterialGenerationEntity orphan = ApplicationMaterialGenerationEntity.builder()
                .vacancy(null)
                .status(ApplicationMaterialGenerationStatus.PENDING)
                .candidateProfileVersion(0L)
                .requestedAt(REQUESTED_AT)
                .build();

        assertThatThrownBy(() -> generationRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingVacancy_cascadesToItsGenerations() {
        Vacancy vacancy = aVacancy("cascade-" + UUID.randomUUID());
        ApplicationMaterialGenerationEntity generation = generationRepository.save(pending(vacancy));
        entityManager.flush();
        UUID vacancyId = vacancy.getId();

        entityManager.clear();
        vacancyRepository.delete(vacancyRepository.findById(vacancyId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(generationRepository.findById(generation.getId())).isEmpty();
    }

    // ==================== 5. Status CHECK constraint ====================

    /**
     * {@code started_at} is deliberately set (not null) here: an arbitrary non-enum status value
     * also fails {@code chk_amg_started_at_matches_status} (whose {@code status = 'PENDING'}
     * comparison is false for any bogus value), and which of the several simultaneously-violated
     * CHECK constraints PostgreSQL reports is not guaranteed by declaration order - shaping this
     * row to otherwise satisfy every lifecycle CHECK isolates {@code chk_amg_status} as the one
     * actually being exercised.
     */
    @Test
    void invalidStatusValue_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("bad-status-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at)
                        VALUES (?1, 'BOGUS', 0, ?2, ?3)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_status");
    }

    // ==================== 6-9. Lifecycle CHECK constraints ====================

    @Test
    void pendingWithStartedAt_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("pending-started-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at)
                        VALUES (?1, 'PENDING', 0, ?2, ?3)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_started_at_matches_status");
    }

    @Test
    void inProgressWithoutStartedAt_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("in-progress-no-started-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at)
                        VALUES (?1, 'IN_PROGRESS', 0, ?2)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_started_at_matches_status");
    }

    @Test
    void completedWithoutCompletedAt_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("completed-no-completed-at-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at)
                        VALUES (?1, 'COMPLETED', 0, ?2, ?3)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_completed_at_matches_status");
    }

    @Test
    void failedWithoutFailureCode_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("failed-no-code-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at, completed_at)
                        VALUES (?1, 'FAILED', 0, ?2, ?3, ?4)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .setParameter(4, COMPLETED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_failure_code_matches_status");
    }

    @Test
    void completedWithFailureCode_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("completed-with-code-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at, completed_at, failure_code)
                        VALUES (?1, 'COMPLETED', 0, ?2, ?3, ?4, 'SOME_CODE')
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .setParameter(4, COMPLETED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_failure_code_matches_status");
    }

    @Test
    void failureMessageWithoutFailureCode_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("message-no-code-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at, completed_at, failure_message)
                        VALUES (?1, 'COMPLETED', 0, ?2, ?3, ?4, 'some detail')
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .setParameter(4, COMPLETED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_failure_message_requires_code");
    }

    @Test
    void startedAtBeforeRequestedAt_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("started-before-requested-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at)
                        VALUES (?1, 'IN_PROGRESS', 0, ?2, ?3)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, REQUESTED_AT.minusSeconds(1))
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_started_after_requested");
    }

    @Test
    void completedAtBeforeStartedAt_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("completed-before-started-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at, started_at, completed_at)
                        VALUES (?1, 'COMPLETED', 0, ?2, ?3, ?4)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .setParameter(3, STARTED_AT)
                        .setParameter(4, STARTED_AT.minusSeconds(1))
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_completed_after_started");
    }

    @Test
    void negativeCandidateProfileVersion_violatesCheckConstraint() {
        Vacancy vacancy = aVacancy("neg-profile-version-" + UUID.randomUUID());
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO application_material_generation
                            (vacancy_id, status, candidate_profile_version, requested_at)
                        VALUES (?1, 'PENDING', -1, ?2)
                        """)
                        .setParameter(1, vacancy.getId())
                        .setParameter(2, REQUESTED_AT)
                        .executeUpdate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("chk_amg_candidate_profile_version_non_negative");
    }

    // ==================== 10. Valid FAILED row accepted ====================

    @Test
    void validFailedGeneration_isAccepted() {
        Vacancy vacancy = aVacancy("valid-failed-" + UUID.randomUUID());

        ApplicationMaterialGenerationEntity failed = ApplicationMaterialGenerationEntity.builder()
                .vacancy(vacancy)
                .status(ApplicationMaterialGenerationStatus.FAILED)
                .candidateProfileVersion(0L)
                .requestedAt(REQUESTED_AT)
                .startedAt(STARTED_AT)
                .completedAt(COMPLETED_AT)
                .failureCode("AI_PROVIDER_ERROR")
                .failureMessage("The AI provider returned an error")
                .build();

        generationRepository.saveAndFlush(failed);
        entityManager.clear();

        ApplicationMaterialGenerationEntity reloaded = generationRepository.findById(failed.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(reloaded.getFailureCode()).isEqualTo("AI_PROVIDER_ERROR");
    }

    // ==================== 11. Optimistic locking ====================

    /**
     * Mirrors {@code CareerHistoryRepositoryTest#staleConcurrentRootUpdate_secondWriterFailsWithOptimisticLockingException}:
     * uses {@code LockModeType.OPTIMISTIC_FORCE_INCREMENT} to force a real version-checked UPDATE
     * (a plain no-op {@code save()} of an unmodified detached copy would never issue one).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleConcurrentUpdate_secondWriterFailsWithOptimisticLockingException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID generationId = tx.execute(status -> generationRepository.save(pending(aVacancy("opt-lock-" + UUID.randomUUID()))).getId());

        ApplicationMaterialGenerationEntity firstWriterCopy =
                tx.execute(status -> generationRepository.findById(generationId).orElseThrow());
        ApplicationMaterialGenerationEntity secondWriterCopy =
                tx.execute(status -> generationRepository.findById(generationId).orElseThrow());

        tx.execute(status -> {
            forceVersionIncrement(firstWriterCopy);
            return null;
        });

        assertThatThrownBy(() -> tx.execute(status -> {
                    forceVersionIncrement(secondWriterCopy);
                    return null;
                }))
                .isInstanceOf(jakarta.persistence.OptimisticLockException.class);

        ApplicationMaterialGenerationEntity finalState =
                tx.execute(status -> generationRepository.findById(generationId).orElseThrow());
        assertThat(finalState.getVersion()).isEqualTo(1L);
    }

    private void forceVersionIncrement(ApplicationMaterialGenerationEntity detachedCopy) {
        ApplicationMaterialGenerationEntity managed = entityManager.merge(detachedCopy);
        entityManager.lock(managed, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    // ==================== Helpers ====================

    private Vacancy aVacancy(String urlSuffix) {
        Company company = companyRepository.save(Company.builder().name("Example Systems " + urlSuffix).build());
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Demo Backend Engineer")
                .url("https://example.test/jobs/" + urlSuffix)
                .canonicalUrl("https://example.test/jobs/" + urlSuffix)
                .build());
    }

    private ApplicationMaterialGenerationEntity pending(Vacancy vacancy) {
        return pendingWithRequestedAt(vacancy, REQUESTED_AT);
    }

    private ApplicationMaterialGenerationEntity pendingWithRequestedAt(Vacancy vacancy, Instant requestedAt) {
        return ApplicationMaterialGenerationEntity.builder()
                .vacancy(vacancy)
                .status(ApplicationMaterialGenerationStatus.PENDING)
                .candidateProfileVersion(0L)
                .requestedAt(requestedAt)
                .build();
    }

    private ApplicationMaterialGenerationEntity pendingWithSourceFingerprint(
            Vacancy vacancy, Instant requestedAt, String sourceFingerprint) {
        return ApplicationMaterialGenerationEntity.builder()
                .vacancy(vacancy)
                .status(ApplicationMaterialGenerationStatus.PENDING)
                .candidateProfileVersion(0L)
                .sourceFingerprint(sourceFingerprint)
                .requestedAt(requestedAt)
                .build();
    }
}
