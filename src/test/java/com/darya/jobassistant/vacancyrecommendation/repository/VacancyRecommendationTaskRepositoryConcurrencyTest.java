package com.darya.jobassistant.vacancyrecommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskStatus;
import com.darya.jobassistant.vacancyrecommendation.entity.VacancyRecommendationTaskEntity;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
 * Real-PostgreSQL proof of {@link VacancyRecommendationTaskRepository}'s two-statement claim
 * ({@link VacancyRecommendationTaskRepository#selectClaimCandidates}/{@link
 * VacancyRecommendationTaskRepository#claimByIds}): eligibility rules, deterministic ordering,
 * batch-size limiting, attempt-count bookkeeping, and - the reason this is a real-database test
 * rather than a Mockito one - genuine {@code FOR UPDATE SKIP LOCKED} behavior under concurrent
 * claimers, mirroring {@code VacancyRepositoryTest}'s and {@code
 * JobDiscoverySchedulerConcurrencyTest}'s concurrency-proof conventions.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class VacancyRecommendationTaskRepositoryConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VacancyRecommendationTaskRepository taskRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        // The two @Transactional(NOT_SUPPORTED) tests below commit real rows for real - unlike
        // every other (ordinarily transactional) test in this class, @DataJpaTest's automatic
        // per-test rollback does not apply to them. Without this cleanup, a row one of them plants
        // would linger in the single shared container's database and silently change which/how
        // many rows a later test's own selectClaimCandidates(...) call sees.
        requiresNewTransaction().executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM vacancy_recommendation_task").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM vacancy").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM company").executeUpdate();
        });
    }

    @Test
    void selectClaimCandidates_pendingTask_isEligible() {
        UUID vacancyId = persistVacancy().getId();
        UUID taskId = persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, null, 0);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId).contains(taskId);
    }

    @Test
    void selectClaimCandidates_retryWaitWithFutureNextAttempt_isNotEligible() {
        UUID vacancyId = persistVacancy().getId();
        Instant future = Instant.now().plus(Duration.ofHours(1));
        UUID taskId = persistTask(vacancyId, VacancyRecommendationTaskStatus.RETRY_WAIT, future, null, null, 1);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId).doesNotContain(taskId);
    }

    @Test
    void selectClaimCandidates_retryWaitWithDueNextAttempt_isEligible() {
        UUID vacancyId = persistVacancy().getId();
        Instant due = Instant.now().minus(Duration.ofMinutes(1));
        UUID taskId = persistTask(vacancyId, VacancyRecommendationTaskStatus.RETRY_WAIT, due, null, null, 1);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId).contains(taskId);
    }

    @Test
    void selectClaimCandidates_processingWithActiveLease_isNotEligible() {
        UUID vacancyId = persistVacancy().getId();
        Instant activeLease = Instant.now().plus(Duration.ofMinutes(20));
        UUID taskId = persistTask(
                vacancyId, VacancyRecommendationTaskStatus.PROCESSING, Instant.now(), activeLease, "worker-1", 1);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId).doesNotContain(taskId);
    }

    @Test
    void selectClaimCandidates_processingWithExpiredLease_isEligible_leaseRecovery() {
        UUID vacancyId = persistVacancy().getId();
        Instant expiredLease = Instant.now().minus(Duration.ofMinutes(1));
        UUID taskId = persistTask(
                vacancyId, VacancyRecommendationTaskStatus.PROCESSING, Instant.now(), expiredLease, "crashed-worker", 1);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId).contains(taskId);
    }

    @Test
    void selectClaimCandidates_completedAndDeadTasks_areNeverEligible() {
        UUID vacancyOne = persistVacancy().getId();
        UUID vacancyTwo = persistVacancy().getId();
        UUID completedId = persistCompletedTask(vacancyOne, VacancyRecommendationTaskStatus.COMPLETED);
        UUID deadId = persistCompletedTask(vacancyTwo, VacancyRecommendationTaskStatus.DEAD);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(50);

        assertThat(candidates).extracting(VacancyRecommendationTaskEntity::getId)
                .doesNotContain(completedId, deadId);
    }

    @Test
    void selectClaimCandidates_ordersByNextAttemptAtThenCreatedAt() {
        UUID vacancyLater = persistVacancy().getId();
        UUID vacancyEarlier = persistVacancy().getId();
        Instant now = Instant.now();
        UUID laterTaskId = persistTask(vacancyLater, VacancyRecommendationTaskStatus.PENDING, now.plusSeconds(60), null, null, 0);
        UUID earlierTaskId = persistTask(vacancyEarlier, VacancyRecommendationTaskStatus.PENDING, now, null, null, 0);

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(10);

        List<UUID> ids = candidates.stream().map(VacancyRecommendationTaskEntity::getId).toList();
        assertThat(ids.indexOf(earlierTaskId)).isLessThan(ids.indexOf(laterTaskId));
    }

    @Test
    void selectClaimCandidates_respectsBatchSizeLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            UUID vacancyId = persistVacancy().getId();
            persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, now.plusSeconds(i), null, null, 0);
        }

        List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(2);

        assertThat(candidates).hasSize(2);
    }

    @Test
    void claimByIds_transitionsToProcessing_incrementsAttemptCountExactlyOnce_setsLeaseAndOwner() {
        UUID vacancyId = persistVacancy().getId();
        UUID taskId = persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, null, 0);

        List<VacancyRecommendationTaskEntity> claimed = taskRepository.claimByIds(List.of(taskId), 1200, "worker-1");

        assertThat(claimed).hasSize(1);
        VacancyRecommendationTaskEntity task = claimed.get(0);
        assertThat(task.getStatus()).isEqualTo(VacancyRecommendationTaskStatus.PROCESSING);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getLeaseOwner()).isEqualTo("worker-1");
        assertThat(task.getLeaseUntil()).isAfter(Instant.now());
    }

    @Test
    void claimByIds_calledTwiceOnSameRetryWaitTask_incrementsAttemptCountAcrossAttempts() {
        UUID vacancyId = persistVacancy().getId();
        UUID taskId = persistTask(vacancyId, VacancyRecommendationTaskStatus.RETRY_WAIT, Instant.now().minusSeconds(1), null, null, 0);

        taskRepository.claimByIds(List.of(taskId), 1200, "worker-1"); // 1st attempt: 0 -> 1
        // Simulate the claim being released back to RETRY_WAIT (as scheduleRetry would) then reclaimed.
        entityManager.createNativeQuery(
                        "UPDATE vacancy_recommendation_task SET status = 'RETRY_WAIT', lease_until = NULL, lease_owner = NULL, next_attempt_at = now() WHERE id = ?1")
                .setParameter(1, taskId)
                .executeUpdate();
        entityManager.clear();

        List<VacancyRecommendationTaskEntity> secondClaim = taskRepository.claimByIds(List.of(taskId), 1200, "worker-2"); // 2nd attempt: 1 -> 2

        assertThat(secondClaim).hasSize(1);
        assertThat(secondClaim.get(0).getAttemptCount()).isEqualTo(2);
    }

    /**
     * The core cross-node correctness proof: two independent transactions race to claim the same
     * single {@code PENDING} task. Exactly one must win - {@code FOR UPDATE SKIP LOCKED} guarantees
     * the loser's {@code selectClaimCandidates} simply excludes the already-locked row rather than
     * blocking or double-claiming it.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentClaimAttempts_onSameTask_exactlyOneWorkerClaimsIt() throws Exception {
        UUID taskId = requiresNewTransaction().execute(status -> {
            UUID vacancyId = persistVacancy().getId();
            return persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, null, 0);
        });

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Optional<VacancyRecommendationTaskEntity>>> futures = new ArrayList<>();
        futures.add(executor.submit(() -> {
            barrier.await();
            return claimOneInOwnTransaction("worker-a");
        }));
        futures.add(executor.submit(() -> {
            barrier.await();
            return claimOneInOwnTransaction("worker-b");
        }));

        List<Optional<VacancyRecommendationTaskEntity>> results = new ArrayList<>();
        for (Future<Optional<VacancyRecommendationTaskEntity>> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }

        long wins = results.stream().filter(Optional::isPresent).count();
        assertThat(wins).isEqualTo(1);

        VacancyRecommendationTaskEntity persisted = taskRepository.findById(taskId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(VacancyRecommendationTaskStatus.PROCESSING);
        assertThat(persisted.getAttemptCount()).isEqualTo(1); // never double-incremented
    }

    /**
     * Proves {@code SKIP LOCKED} itself is non-blocking: while node A's transaction holds the row
     * lock open (deliberately not yet committed), node B's own {@code selectClaimCandidates} call
     * must return immediately with zero candidates rather than waiting for A's lock to release.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void selectClaimCandidates_secondCaller_doesNotBlockOnFirstCallersOpenLock() throws Exception {
        requiresNewTransaction().executeWithoutResult(status -> {
            UUID vacancyId = persistVacancy().getId();
            persistTask(vacancyId, VacancyRecommendationTaskStatus.PENDING, Instant.now(), null, null, 0);
        });

        CountDownLatch nodeALockedRow = new CountDownLatch(1);
        CountDownLatch releaseNodeA = new CountDownLatch(1);
        Future<?> nodeA = executor.submit(() -> requiresNewTransaction().execute(status -> {
            taskRepository.selectClaimCandidates(10);
            nodeALockedRow.countDown();
            try {
                assertThat(releaseNodeA.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));

        assertThat(nodeALockedRow.await(10, TimeUnit.SECONDS)).isTrue();

        Instant before = Instant.now();
        List<VacancyRecommendationTaskEntity> nodeBCandidates =
                requiresNewTransaction().execute(status -> taskRepository.selectClaimCandidates(10));
        Duration elapsed = Duration.between(before, Instant.now());

        releaseNodeA.countDown();
        nodeA.get(10, TimeUnit.SECONDS);

        assertThat(nodeBCandidates).isEmpty(); // skipped the locked row, not blocked on it
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    private Optional<VacancyRecommendationTaskEntity> claimOneInOwnTransaction(String owner) {
        return requiresNewTransaction().execute(status -> {
            List<VacancyRecommendationTaskEntity> candidates = taskRepository.selectClaimCandidates(1);
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            List<UUID> ids = candidates.stream().map(VacancyRecommendationTaskEntity::getId).toList();
            List<VacancyRecommendationTaskEntity> claimed = taskRepository.claimByIds(ids, 1200, owner);
            return claimed.stream().findFirst();
        });
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private UUID persistTask(UUID vacancyId, VacancyRecommendationTaskStatus status, Instant nextAttemptAt,
                              Instant leaseUntil, String leaseOwner, int attemptCount) {
        VacancyRecommendationTaskEntity task = taskRepository.save(VacancyRecommendationTaskEntity.builder()
                .vacancyId(vacancyId)
                .status(status)
                .attemptCount(attemptCount)
                .nextAttemptAt(nextAttemptAt)
                .leaseUntil(leaseUntil)
                .leaseOwner(leaseOwner)
                .build());
        entityManager.flush();
        entityManager.clear();
        return task.getId();
    }

    private UUID persistCompletedTask(UUID vacancyId, VacancyRecommendationTaskStatus terminalStatus) {
        VacancyRecommendationTaskEntity task = taskRepository.save(VacancyRecommendationTaskEntity.builder()
                .vacancyId(vacancyId)
                .status(terminalStatus)
                .outcome(terminalStatus == VacancyRecommendationTaskStatus.COMPLETED
                        ? com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskOutcome.NOTIFIED
                        : com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskOutcome.PERMANENT_FAILURE)
                .attemptCount(1)
                .nextAttemptAt(Instant.now())
                .completedAt(Instant.now())
                .build());
        entityManager.flush();
        entityManager.clear();
        return task.getId();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        String url = "https://example.com/job/" + UUID.randomUUID();
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url(url)
                .canonicalUrl(url)
                .source("remoteok")
                .build());
        entityManager.flush();
        return vacancy;
    }
}
