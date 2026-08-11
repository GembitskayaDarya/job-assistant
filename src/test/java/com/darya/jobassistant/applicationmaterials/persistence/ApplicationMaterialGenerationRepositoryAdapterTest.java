package com.darya.jobassistant.applicationmaterials.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationActiveConflictException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationConcurrentModificationException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationVacancyNotFoundException;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 10 Step 1: proves {@link ApplicationMaterialGenerationRepositoryAdapter} against real
 * PostgreSQL - create, version-checked update, vacancy-not-found translation, optimistic
 * concurrency, and newest-first listing through the port. Mirrors {@code
 * CareerHistoryRepositoryAdapterTest}'s {@code @DataJpaTest}/Testcontainers setup, building the
 * adapter directly (a plain {@code @Repository} class, not a Spring Data interface).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class ApplicationMaterialGenerationRepositoryAdapterTest {

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

    private static final Instant REQUESTED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private ApplicationMaterialGenerationRepositoryAdapter adapter() {
        return new ApplicationMaterialGenerationRepositoryAdapter(generationRepository, vacancyRepository, Clock.systemUTC());
    }

    // ==================== Create ====================

    @Test
    void save_newGeneration_persistsAndReturnsItWithDurableIdAndZeroVersion() {
        UUID vacancyId = aVacancy("create-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration requested =
                ApplicationMaterialGeneration.requestNew(vacancyId, 2L, 5L, REQUESTED_AT);

        ApplicationMaterialGeneration saved = adapter().save(requested);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.vacancyId()).isEqualTo(vacancyId);
        assertThat(saved.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(saved.candidateProfileVersion()).isEqualTo(2L);
        assertThat(saved.careerHistoryVersion()).isEqualTo(5L);
        assertThat(saved.version()).isZero();
    }

    @Test
    void save_newGenerationForUnknownVacancy_throwsVacancyNotFound() {
        ApplicationMaterialGeneration requested =
                ApplicationMaterialGeneration.requestNew(UUID.randomUUID(), 0L, null, REQUESTED_AT);

        assertThatThrownBy(() -> adapter().save(requested))
                .isInstanceOf(ApplicationMaterialGenerationVacancyNotFoundException.class);
    }

    // ==================== Round trip / findById / findByVacancyId ====================

    @Test
    void findById_afterSave_returnsTheSameGeneration() {
        UUID vacancyId = aVacancy("find-by-id-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration saved =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));

        Optional<ApplicationMaterialGeneration> reloaded = adapter().findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().id()).isEqualTo(saved.id());
        assertThat(reloaded.get().vacancyId()).isEqualTo(vacancyId);
    }

    @Test
    void findById_missingGeneration_returnsEmpty() {
        assertThat(adapter().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByVacancyId_multipleGenerations_returnsAllOfThem() {
        // Distinct candidateProfileVersions - three simultaneously PENDING generations for the same
        // vacancy and the same effective key would now collide with V25's active-uniqueness index
        // (see the "V25 active-uniqueness" section below); this test is about listing, not reuse.
        UUID vacancyId = aVacancy("multi-" + UUID.randomUUID()).getId();
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 2L, null, REQUESTED_AT.plusSeconds(1)));
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 3L, null, REQUESTED_AT.plusSeconds(2)));

        List<ApplicationMaterialGeneration> generations = adapter().findByVacancyId(vacancyId);

        assertThat(generations).hasSize(3);
    }

    @Test
    void findByVacancyId_ordersNewestRequestedFirst() {
        // Distinct candidateProfileVersions - see findByVacancyId_multipleGenerations_returnsAllOfThem.
        UUID vacancyId = aVacancy("ordering-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration oldest =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        ApplicationMaterialGeneration newest = adapter().save(
                ApplicationMaterialGeneration.requestNew(vacancyId, 2L, null, REQUESTED_AT.plus(1, ChronoUnit.DAYS)));

        List<ApplicationMaterialGeneration> generations = adapter().findByVacancyId(vacancyId);

        assertThat(generations).extracting(ApplicationMaterialGeneration::id)
                .containsExactly(newest.id(), oldest.id());
    }

    @Test
    void findByVacancyId_unrelatedVacancy_isNeverReturned() {
        UUID vacancyA = aVacancy("isolation-a-" + UUID.randomUUID()).getId();
        UUID vacancyB = aVacancy("isolation-b-" + UUID.randomUUID()).getId();
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyA, 1L, null, REQUESTED_AT));

        assertThat(adapter().findByVacancyId(vacancyB)).isEmpty();
    }

    // ==================== Update / lifecycle transitions ====================

    @Test
    void save_startedGeneration_persistsLifecycleFields() {
        UUID vacancyId = aVacancy("start-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration pending =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        Instant startedAt = REQUESTED_AT.plusSeconds(5);

        ApplicationMaterialGeneration started = adapter().save(pending.start(startedAt));

        assertThat(started.status()).isEqualTo(ApplicationMaterialGenerationStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isEqualTo(startedAt);
        assertThat(started.version()).isEqualTo(1L);

        ApplicationMaterialGeneration reloaded = adapter().findById(started.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ApplicationMaterialGenerationStatus.IN_PROGRESS);
        assertThat(reloaded.startedAt()).isEqualTo(startedAt);
    }

    @Test
    void save_completedGeneration_persistsCompletionFields() {
        UUID vacancyId = aVacancy("complete-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration started = adapter()
                .save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT))
                .start(REQUESTED_AT.plusSeconds(1));
        ApplicationMaterialGeneration savedStarted = adapter().save(started);
        Instant completedAt = REQUESTED_AT.plusSeconds(2);

        ApplicationMaterialGeneration completed = adapter().save(savedStarted.complete(completedAt));

        assertThat(completed.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(completedAt);
        assertThat(completed.version()).isEqualTo(2L);
    }

    @Test
    void save_failedGeneration_persistsFailureMetadata() {
        UUID vacancyId = aVacancy("fail-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration savedStarted = adapter()
                .save(adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT))
                        .start(REQUESTED_AT.plusSeconds(1)));
        Instant completedAt = REQUESTED_AT.plusSeconds(2);

        ApplicationMaterialGeneration failed =
                adapter().save(savedStarted.fail("AI_PROVIDER_ERROR", "The AI provider returned an error", completedAt));

        assertThat(failed.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(failed.failureMessage()).isEqualTo("The AI provider returned an error");

        ApplicationMaterialGeneration reloaded = adapter().findById(failed.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(reloaded.failureCode()).isEqualTo("AI_PROVIDER_ERROR");
    }

    // ==================== Optimistic concurrency ====================

    @Test
    void save_staleVersion_throwsConcurrentModification() {
        UUID vacancyId = aVacancy("stale-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration saved =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        Instant startedAt = REQUESTED_AT.plusSeconds(1);

        // First writer succeeds and advances the row to version 1.
        adapter().save(saved.start(startedAt));

        // Second writer still holds the stale version-0 copy.
        assertThatThrownBy(() -> adapter().save(saved.start(startedAt)))
                .isInstanceOf(ApplicationMaterialGenerationConcurrentModificationException.class);
    }

    @Test
    void save_afterConcurrentModification_leavesTheWinningWriteIntact() {
        UUID vacancyId = aVacancy("stale-preserved-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration saved =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        Instant startedAt = REQUESTED_AT.plusSeconds(1);

        ApplicationMaterialGeneration started = adapter().save(saved.start(startedAt));
        assertThatThrownBy(() -> adapter().save(saved.start(startedAt)))
                .isInstanceOf(ApplicationMaterialGenerationConcurrentModificationException.class);

        ApplicationMaterialGeneration reloaded = adapter().findById(started.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ApplicationMaterialGenerationStatus.IN_PROGRESS);
        assertThat(reloaded.version()).isEqualTo(1L);
    }

    // ==================== V25 active-uniqueness (production-readiness acceptance fix) ====================

    @Test
    void save_secondPendingForSameEffectiveKey_throwsActiveConflict() {
        UUID vacancyId = aVacancy("active-conflict-" + UUID.randomUUID()).getId();
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 5L, 2L, REQUESTED_AT));

        assertThatThrownBy(() -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 5L, 2L, REQUESTED_AT.plusSeconds(1))))
                .isInstanceOf(ApplicationMaterialGenerationActiveConflictException.class);
    }

    @Test
    void save_secondPendingForSameEffectiveKey_nullCareerHistoryVersion_throwsActiveConflict() {
        // NULLS NOT DISTINCT (V25): two rows both with career_history_version IS NULL must collide
        // exactly like any other equal value, not be treated as distinct.
        UUID vacancyId = aVacancy("active-conflict-null-" + UUID.randomUUID()).getId();
        adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 3L, null, REQUESTED_AT));

        assertThatThrownBy(() -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 3L, null, REQUESTED_AT.plusSeconds(1))))
                .isInstanceOf(ApplicationMaterialGenerationActiveConflictException.class);
    }

    @Test
    void save_secondPendingWhileFirstIsInProgress_throwsActiveConflict() {
        UUID vacancyId = aVacancy("active-conflict-in-progress-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration inProgress =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        adapter().save(inProgress.start(REQUESTED_AT.plusSeconds(1)));

        assertThatThrownBy(() -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT.plusSeconds(2))))
                .isInstanceOf(ApplicationMaterialGenerationActiveConflictException.class);
    }

    @Test
    void save_newPendingAlongsideCompletedGenerationForSameKey_succeeds() {
        UUID vacancyId = aVacancy("completed-coexists-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration completed = adapter()
                .save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT))
                .start(REQUESTED_AT.plusSeconds(1));
        adapter().save(adapter().save(completed).complete(REQUESTED_AT.plusSeconds(2)));

        ApplicationMaterialGeneration secondPending =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT.plusSeconds(3)));

        assertThat(secondPending.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(adapter().findByVacancyId(vacancyId)).hasSize(2);
    }

    @Test
    void save_newPendingAfterFailedGenerationForSameKey_succeeds() {
        UUID vacancyId = aVacancy("failed-does-not-block-" + UUID.randomUUID()).getId();
        ApplicationMaterialGeneration started = adapter()
                .save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT))
                .start(REQUESTED_AT.plusSeconds(1));
        adapter().save(adapter().save(started).fail("AI_PROVIDER_ERROR", "boom", REQUESTED_AT.plusSeconds(2)));

        ApplicationMaterialGeneration secondPending =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT.plusSeconds(3)));

        assertThat(secondPending.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
    }

    @Test
    void save_activePendingForDifferentVacancies_bothSucceed() {
        UUID vacancyA = aVacancy("independent-vacancy-a-" + UUID.randomUUID()).getId();
        UUID vacancyB = aVacancy("independent-vacancy-b-" + UUID.randomUUID()).getId();

        ApplicationMaterialGeneration a = adapter().save(ApplicationMaterialGeneration.requestNew(vacancyA, 1L, null, REQUESTED_AT));
        ApplicationMaterialGeneration b = adapter().save(ApplicationMaterialGeneration.requestNew(vacancyB, 1L, null, REQUESTED_AT));

        assertThat(a.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(b.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
    }

    @Test
    void save_activePendingForDifferentCandidateProfileVersions_bothSucceed() {
        UUID vacancyId = aVacancy("independent-profile-version-" + UUID.randomUUID()).getId();

        ApplicationMaterialGeneration a = adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        ApplicationMaterialGeneration b = adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 2L, null, REQUESTED_AT.plusSeconds(1)));

        assertThat(a.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(b.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(adapter().findByVacancyId(vacancyId)).hasSize(2);
    }

    @Test
    void save_activePendingForDifferentCareerHistoryVersions_bothSucceed() {
        UUID vacancyId = aVacancy("independent-history-version-" + UUID.randomUUID()).getId();

        ApplicationMaterialGeneration withoutHistory =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT));
        ApplicationMaterialGeneration withHistory =
                adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, 1L, REQUESTED_AT.plusSeconds(1)));

        assertThat(withoutHistory.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(withHistory.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(adapter().findByVacancyId(vacancyId)).hasSize(2);
    }

    /**
     * The AI-cost invariant proven with genuine concurrency rather than sequential calls - two
     * threads racing to insert the very first generation for the exact same effective key
     * (including {@code careerHistoryVersion == null}, the case ordinary unique-index semantics get
     * wrong without {@code NULLS NOT DISTINCT}). Disables the surrounding {@code @DataJpaTest}
     * per-test transaction (mirrors {@code CareerHistoryImportUseCaseConcurrencyTest}) so each
     * thread's own transaction genuinely commits and can conflict with the other's.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_concurrentFirstInsertsForSameEffectiveKey_nullCareerHistoryVersion_exactlyOneWins() throws Exception {
        UUID vacancyId = aVacancy("concurrent-null-history-" + UUID.randomUUID()).getId();

        List<Object> results = runConcurrently(
                () -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT)),
                () -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, REQUESTED_AT)));

        assertExactlyOneWinnerAndOneConflict(results);
        assertThat(adapter().findByVacancyId(vacancyId)).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_concurrentFirstInsertsForSameEffectiveKey_withCareerHistoryVersion_exactlyOneWins() throws Exception {
        UUID vacancyId = aVacancy("concurrent-with-history-" + UUID.randomUUID()).getId();

        List<Object> results = runConcurrently(
                () -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 4L, 7L, REQUESTED_AT)),
                () -> adapter().save(ApplicationMaterialGeneration.requestNew(vacancyId, 4L, 7L, REQUESTED_AT)));

        assertExactlyOneWinnerAndOneConflict(results);
        assertThat(adapter().findByVacancyId(vacancyId)).hasSize(1);
    }

    private void assertExactlyOneWinnerAndOneConflict(List<Object> results) {
        long winners = results.stream().filter(r -> r instanceof ApplicationMaterialGeneration).count();
        long conflicts = results.stream().filter(r -> r instanceof ApplicationMaterialGenerationActiveConflictException).count();
        assertThat(winners).as("exactly one concurrent insert should win: %s", results).isEqualTo(1);
        assertThat(conflicts).as("exactly one concurrent insert should be rejected: %s", results).isEqualTo(1);
    }

    @FunctionalInterface
    private interface GenerationSupplier {
        ApplicationMaterialGeneration get();
    }

    private List<Object> runConcurrently(GenerationSupplier first, GenerationSupplier second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> attempt(barrier, first)));
            futures.add(executor.submit(() -> attempt(barrier, second)));
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private Object attempt(CyclicBarrier barrier, GenerationSupplier supplier) throws Exception {
        barrier.await();
        try {
            return supplier.get();
        } catch (ApplicationMaterialGenerationActiveConflictException e) {
            return e;
        }
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
}
