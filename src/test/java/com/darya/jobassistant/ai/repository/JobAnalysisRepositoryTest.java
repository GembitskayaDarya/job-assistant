package com.darya.jobassistant.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class JobAnalysisRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");

    @Autowired
    private JobAnalysisRepository jobAnalysisRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void claimIfAbsent_newVacancy_insertsInProgressRow() {
        UUID vacancyId = persistVacancy().getId();

        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getVacancyId()).isEqualTo(vacancyId);
        assertThat(claimed.get().getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(claimed.get().getScore()).isNull();
        assertThat(claimed.get().getSummary()).isNull();
    }

    @Test
    void claimIfAbsent_secondCallForSameVacancy_returnsEmpty() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();

        Optional<JobAnalysisEntity> second = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);

        assertThat(second).isEmpty();
        assertThat(countByVacancyId(vacancyId)).isEqualTo(1);
    }

    @Test
    void claimIfAbsent_vacancyAlreadyHasCompletedAnalysis_returnsEmptyAndDoesNotOverwrite() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.persist(vacancyId, analysis(), AnalysisOrigin.MONITORING);
        entityManager.flush();

        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);

        assertThat(claimed).isEmpty();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isPresent();
    }

    @Test
    void completeClaim_inProgressClaim_transitionsToCompletedAndStoresResultWithListsSurvivingRoundTrip() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();
        JobAnalysis analysis = new JobAnalysis(
                77, List.of("Strong Java", "Kafka experience"), List.of("No AWS"), List.of("Kubernetes"), List.of("Terraform"),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Good overall match");

        boolean applied = jobAnalysisRepository.completeClaim(vacancyId, analysis, NOW.plusSeconds(5));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        Optional<PersistedJobAnalysis> persisted = jobAnalysisRepository.findCompletedByVacancyId(vacancyId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().analysis()).isEqualTo(analysis);
        assertThat(persisted.get().analysis().pros()).containsExactly("Strong Java", "Kafka experience");
        assertThat(persisted.get().analysis().cons()).containsExactly("No AWS");
        assertThat(persisted.get().analysis().missingRequiredSkills()).containsExactly("Kubernetes");
        assertThat(persisted.get().analysis().missingPreferredSkills()).containsExactly("Terraform");
        assertThat(persisted.get().analysis().experienceAssessment()).isEqualTo("6 years vs. 5+ requested - requirement met.");
        assertThat(persisted.get().analysis().preferencesAssessment()).isEqualTo("Remote preference matches.");
    }

    @Test
    void completeClaim_noInProgressClaim_updatesNothing() {
        UUID vacancyId = persistVacancy().getId();

        boolean applied = jobAnalysisRepository.completeClaim(vacancyId, analysis(), NOW);

        assertThat(applied).isFalse();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isEmpty();
    }

    @Test
    void completeClaim_alreadyCompleted_doesNotOverwriteTheExistingAnalysis() {
        UUID vacancyId = persistVacancy().getId();
        JobAnalysis original = analysis();
        jobAnalysisRepository.persist(vacancyId, original, AnalysisOrigin.MONITORING);
        entityManager.flush();

        boolean applied = jobAnalysisRepository.completeClaim(
                vacancyId,
                new JobAnalysis(1, List.of(), List.of(), List.of(), List.of(), "Not assessed.", "Not assessed.", "different"),
                NOW);
        entityManager.clear();

        assertThat(applied).isFalse();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId).orElseThrow().analysis()).isEqualTo(original);
    }

    @Test
    void reclaimStaleClaim_freshClaim_isNotReclaimed() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();

        boolean reclaimed = jobAnalysisRepository.reclaimStaleClaim(vacancyId, NOW.plusSeconds(10), NOW.minusSeconds(1));

        assertThat(reclaimed).isFalse();
    }

    @Test
    void reclaimStaleClaim_staleClaim_isReclaimedAndUpdatedAtAdvances() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();
        Instant later = NOW.plusSeconds(600);
        Instant staleThreshold = NOW.plusSeconds(120);

        boolean reclaimed = jobAnalysisRepository.reclaimStaleClaim(vacancyId, later, staleThreshold);
        entityManager.flush();
        entityManager.clear();

        assertThat(reclaimed).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void releaseClaim_inProgressClaim_deletesRowAndAllowsANewClaimAfterwards() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();

        boolean released = jobAnalysisRepository.releaseClaim(vacancyId);
        entityManager.flush();
        entityManager.clear();

        assertThat(released).isTrue();
        assertThat(countByVacancyId(vacancyId)).isEqualTo(0);

        Optional<JobAnalysisEntity> reclaimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW.plusSeconds(1), AnalysisOrigin.MANUAL, NOW.plusSeconds(1));
        assertThat(reclaimed).isPresent();
    }

    @Test
    void releaseClaim_completedAnalysis_doesNotDeleteIt() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.persist(vacancyId, analysis(), AnalysisOrigin.MONITORING);
        entityManager.flush();

        boolean released = jobAnalysisRepository.releaseClaim(vacancyId);

        assertThat(released).isFalse();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isPresent();
    }

    @Test
    void findCompletedByVacancyId_inProgressClaim_returnsEmpty() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        entityManager.flush();

        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isEmpty();
    }

    @Test
    void persist_plainInsertPath_storesCompletedAnalysis() {
        UUID vacancyId = persistVacancy().getId();

        PersistedJobAnalysis persisted = jobAnalysisRepository.persist(vacancyId, analysis(), AnalysisOrigin.MONITORING);
        entityManager.flush();
        entityManager.clear();

        assertThat(persisted.analysis()).isEqualTo(analysis());
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void claimIfAbsent_concurrentCallsForSameVacancy_exactlyOneWins() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID vacancyId = transactionTemplate.execute(status -> persistVacancy().getId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return transactionTemplate.execute(
                            status -> jobAnalysisRepository.claimIfAbsent(vacancyId, NOW, AnalysisOrigin.MANUAL, NOW).isPresent());
                }));
            }

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long winners = results.stream().filter(Boolean::booleanValue).count();
            assertThat(winners).isEqualTo(1);
            Long rowCount = transactionTemplate.execute(status -> countByVacancyId(vacancyId));
            assertThat(rowCount).isEqualTo(1L);
        } finally {
            executor.shutdown();
        }
    }

    /** Sprint 9 Step 9 scenario C: brand-new rows, via both entry points, are stamped at {@link JobAnalysisModelVersion#CURRENT}. */
    @Test
    void newlyCreatedRows_useCurrentModelVersion() {
        UUID claimedVacancyId = persistVacancy().getId();
        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(claimedVacancyId, NOW, AnalysisOrigin.MANUAL, NOW);
        UUID persistedVacancyId = persistVacancy().getId();
        PersistedJobAnalysis persisted = jobAnalysisRepository.persist(persistedVacancyId, analysis(), AnalysisOrigin.MONITORING);
        entityManager.flush();
        entityManager.clear();

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getAnalysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        assertThat(persisted.analysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        JobAnalysisEntity reloadedPersisted = jobAnalysisRepository.findByVacancyId(persistedVacancyId).orElseThrow();
        assertThat(reloadedPersisted.getAnalysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
    }

    /**
     * Sprint 9 Step 9 scenario A: a real, pre-existing {@code analysisVersion=2} row (exactly the
     * shape production data was left in before this sprint's {@code CURRENT} bump to 3) is
     * eligible for reanalysis.
     */
    @Test
    void attemptReanalysisClaim_version2CompletedRow_isEligibleWhenCurrentIsThree() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 2, AnalysisOrigin.MONITORING);
        entityManager.flush();
        entityManager.clear();

        boolean claimed = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(claimed).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getReanalysisTargetVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(2);
    }

    /**
     * Sprint 9 Step 9 scenario D: a successful reanalysis of a real {@code analysisVersion=2} row
     * upgrades it to {@link JobAnalysisModelVersion#CURRENT} (3) via exactly one row update - the
     * same atomic {@code completeReanalysisIfClaimOwned} statement every other reanalysis uses.
     */
    @Test
    void completeReanalysis_version2Row_upgradesToCurrentModelVersionThree() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 2, AnalysisOrigin.MONITORING);
        entityManager.flush();
        jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();
        JobAnalysis newAnalysis = new JobAnalysis(
                92, List.of("Even stronger match"), List.of(), List.of(), List.of(),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Great match");

        boolean applied = jobAnalysisRepository.completeReanalysis(vacancyId, newAnalysis, NOW, NOW.plusSeconds(10));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT).isEqualTo(3);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.MANUAL);
        assertThat(JobAnalysisRepository.toDomain(reloaded).analysis()).isEqualTo(newAnalysis);
    }

    /**
     * Sprint 9 Step 9 scenario E: the existing exactly-one-winner concurrency guarantee, exercised
     * against the real production shape (existing analysis at version 2, {@code CURRENT}=3) rather
     * than the generic version-1 fixture above.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void attemptReanalysisClaim_concurrentCallsForVersion2Row_exactlyOneWinsWhenCurrentIsThree() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID vacancyId = transactionTemplate.execute(status -> {
            UUID id = persistVacancy().getId();
            persistCompletedAnalysis(id, analysis(), 2, AnalysisOrigin.MONITORING);
            return id;
        });
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return transactionTemplate.execute(
                            status -> jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1)));
                }));
            }

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long winners = results.stream().filter(Boolean::booleanValue).count();
            assertThat(winners).isEqualTo(1);
            JobAnalysisEntity reloaded = transactionTemplate.execute(
                    status -> jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow());
            assertThat(reloaded.getAnalysisVersion()).isEqualTo(2);
            assertThat(reloaded.getReanalysisTargetVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void attemptReanalysisClaim_outdatedCompletedRow_claimsSuccessfullyAndPreservesOldContent() {
        UUID vacancyId = persistVacancy().getId();
        JobAnalysis original = analysis();
        persistCompletedAnalysis(vacancyId, original, 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        entityManager.clear();

        boolean claimed = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(claimed).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(reloaded.getReanalysisTargetVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        assertThat(reloaded.getReanalysisClaimedAt()).isEqualTo(NOW);
        // The old content, version and origin stay exactly as they were while the claim is held.
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(1);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.MONITORING);
        assertThat(JobAnalysisRepository.toDomain(reloaded).analysis()).isEqualTo(original);
    }

    @Test
    void attemptReanalysisClaim_alreadyCurrentVersionRow_isNotEligible() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL);
        entityManager.flush();

        boolean claimed = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));

        assertThat(claimed).isFalse();
    }

    @Test
    void attemptReanalysisClaim_freshExistingClaim_cannotBeStolenBySecondCaller() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        boolean first = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();

        boolean second = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW.plusSeconds(1), NOW.minusSeconds(1));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    @Test
    void attemptReanalysisClaim_staleExistingClaim_canBeReclaimed() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        boolean first = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        Instant later = NOW.plusSeconds(600);
        Instant staleThreshold = NOW.plusSeconds(120);
        boolean reclaimed = jobAnalysisRepository.attemptReanalysisClaim(vacancyId, later, staleThreshold);
        entityManager.flush();
        entityManager.clear();

        assertThat(first).isTrue();
        assertThat(reclaimed).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getReanalysisClaimedAt()).isEqualTo(later);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void attemptReanalysisClaim_concurrentCallsForSameVacancy_exactlyOneWins() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID vacancyId = transactionTemplate.execute(status -> {
            UUID id = persistVacancy().getId();
            persistCompletedAnalysis(id, analysis(), 1, AnalysisOrigin.MONITORING);
            return id;
        });
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return transactionTemplate.execute(
                            status -> jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1)));
                }));
            }

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long winners = results.stream().filter(Boolean::booleanValue).count();
            assertThat(winners).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void completeReanalysis_ownedClaim_updatesContentVersionAndOriginAndClearsClaimFields() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();
        JobAnalysis newAnalysis = new JobAnalysis(
                92, List.of("Even stronger match"), List.of(), List.of(), List.of(),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Great match");

        boolean applied = jobAnalysisRepository.completeReanalysis(vacancyId, newAnalysis, NOW, NOW.plusSeconds(10));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(JobAnalysisRepository.toDomain(reloaded).analysis()).isEqualTo(newAnalysis);
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.MANUAL);
        assertThat(reloaded.getReanalysisTargetVersion()).isNull();
        assertThat(reloaded.getReanalysisClaimedAt()).isNull();
    }

    @Test
    void completeReanalysis_claimNoLongerOwned_doesNotOverwriteOldContent() {
        UUID vacancyId = persistVacancy().getId();
        JobAnalysis original = analysis();
        persistCompletedAnalysis(vacancyId, original, 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        JobAnalysis newAnalysis = new JobAnalysis(
                92, List.of("Even stronger match"), List.of(), List.of(), List.of(),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Great match");

        // No claim was ever taken (reanalysis_claimed_at is null), so this claimedAt token can
        // never match - simulating "our claim was reclaimed by someone else in the meantime".
        boolean applied = jobAnalysisRepository.completeReanalysis(vacancyId, newAnalysis, NOW, NOW.plusSeconds(10));
        entityManager.clear();

        assertThat(applied).isFalse();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(JobAnalysisRepository.toDomain(reloaded).analysis()).isEqualTo(original);
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(1);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.MONITORING);
    }

    @Test
    void releaseReanalysisClaim_ownedClaim_clearsClaimFieldsAndPreservesOldContentVersionAndOrigin() {
        UUID vacancyId = persistVacancy().getId();
        JobAnalysis original = analysis();
        persistCompletedAnalysis(vacancyId, original, 1, AnalysisOrigin.MONITORING);
        entityManager.flush();
        jobAnalysisRepository.attemptReanalysisClaim(vacancyId, NOW, NOW.minusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        boolean released = jobAnalysisRepository.releaseReanalysisClaim(vacancyId, NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(released).isTrue();
        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(reloaded.getReanalysisTargetVersion()).isNull();
        assertThat(reloaded.getReanalysisClaimedAt()).isNull();
        assertThat(reloaded.getAnalysisVersion()).isEqualTo(1);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.MONITORING);
        assertThat(JobAnalysisRepository.toDomain(reloaded).analysis()).isEqualTo(original);
    }

    @Test
    void releaseReanalysisClaim_notOwned_doesNothing() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.MONITORING);
        entityManager.flush();

        boolean released = jobAnalysisRepository.releaseReanalysisClaim(vacancyId, NOW);

        assertThat(released).isFalse();
    }

    @Test
    void markManuallyReviewedIfAbsent_neverReviewedRow_setsTheTimestamp() {
        UUID vacancyId = persistVacancy().getId();
        persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.AUTOMATIC_DISCOVERY);
        entityManager.flush();
        entityManager.clear();

        jobAnalysisRepository.markManuallyReviewedIfAbsent(vacancyId, NOW);
        entityManager.flush();
        entityManager.clear();

        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getManuallyReviewedAt()).isEqualTo(NOW);
        assertThat(reloaded.getAnalysisOrigin()).isEqualTo(AnalysisOrigin.AUTOMATIC_DISCOVERY);
    }

    @Test
    void markManuallyReviewedIfAbsent_alreadyReviewedRow_doesNotOverwriteTheEarlierTimestamp() {
        UUID vacancyId = persistVacancy().getId();
        Instant firstReview = NOW.minusSeconds(3600);
        JobAnalysisEntity entity = persistCompletedAnalysis(vacancyId, analysis(), 1, AnalysisOrigin.MANUAL);
        entity.setManuallyReviewedAt(firstReview);
        jobAnalysisRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        jobAnalysisRepository.markManuallyReviewedIfAbsent(vacancyId, NOW);
        entityManager.flush();
        entityManager.clear();

        JobAnalysisEntity reloaded = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(reloaded.getManuallyReviewedAt()).isEqualTo(firstReview);
    }

    @Test
    void markManuallyReviewedIfAbsent_noRowForVacancy_doesNothingAndDoesNotThrow() {
        UUID vacancyId = persistVacancy().getId();

        jobAnalysisRepository.markManuallyReviewedIfAbsent(vacancyId, NOW);

        assertThat(countByVacancyId(vacancyId)).isZero();
    }

    private JobAnalysisEntity persistCompletedAnalysis(UUID vacancyId, JobAnalysis analysis, int version, AnalysisOrigin origin) {
        return jobAnalysisRepository.save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .status(AnalysisStatus.COMPLETED)
                .score(analysis.score())
                .summary(analysis.summary())
                .pros(analysis.pros())
                .cons(analysis.cons())
                .missingRequiredSkills(analysis.missingRequiredSkills())
                .missingPreferredSkills(analysis.missingPreferredSkills())
                .experienceAssessment(analysis.experienceAssessment())
                .preferencesAssessment(analysis.preferencesAssessment())
                .analysisVersion(version)
                .analysisOrigin(origin)
                .build());
    }

    private long countByVacancyId(UUID vacancyId) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM job_analysis WHERE vacancy_id = ?1")
                        .setParameter(1, vacancyId)
                        .getSingleResult())
                .longValue();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        String url = "https://example.com/job/" + UUID.randomUUID();
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url(url)
                // canonical_url is NOT NULL since Sprint 8 Step 4B2D (V13) - this test isn't about
                // canonicalization, so the raw url is reused verbatim rather than pulling in
                // VacancyUrlCanonicalizer; it only needs to be non-null and unique here.
                .canonicalUrl(url)
                .build());
        entityManager.flush();
        return vacancy;
    }

    private JobAnalysis analysis() {
        return new JobAnalysis(
                85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");
    }
}
