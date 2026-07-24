package com.darya.jobassistant.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.JobAnalysis;
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

        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
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
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
        entityManager.flush();

        Optional<JobAnalysisEntity> second = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);

        assertThat(second).isEmpty();
        assertThat(countByVacancyId(vacancyId)).isEqualTo(1);
    }

    @Test
    void claimIfAbsent_vacancyAlreadyHasCompletedAnalysis_returnsEmptyAndDoesNotOverwrite() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.persist(vacancyId, analysis());
        entityManager.flush();

        Optional<JobAnalysisEntity> claimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);

        assertThat(claimed).isEmpty();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isPresent();
    }

    @Test
    void completeClaim_inProgressClaim_transitionsToCompletedAndStoresResultWithListsSurvivingRoundTrip() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
        entityManager.flush();
        JobAnalysis analysis = new JobAnalysis(
                77, List.of("Strong Java", "Kafka experience"), List.of("No AWS"), List.of("Kubernetes"), "Good overall match");

        boolean applied = jobAnalysisRepository.completeClaim(vacancyId, analysis, NOW.plusSeconds(5));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        Optional<PersistedJobAnalysis> persisted = jobAnalysisRepository.findCompletedByVacancyId(vacancyId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().analysis()).isEqualTo(analysis);
        assertThat(persisted.get().analysis().pros()).containsExactly("Strong Java", "Kafka experience");
        assertThat(persisted.get().analysis().cons()).containsExactly("No AWS");
        assertThat(persisted.get().analysis().missingSkills()).containsExactly("Kubernetes");
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
        jobAnalysisRepository.persist(vacancyId, original);
        entityManager.flush();

        boolean applied = jobAnalysisRepository.completeClaim(vacancyId, new JobAnalysis(1, List.of(), List.of(), List.of(), "different"), NOW);
        entityManager.clear();

        assertThat(applied).isFalse();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId).orElseThrow().analysis()).isEqualTo(original);
    }

    @Test
    void reclaimStaleClaim_freshClaim_isNotReclaimed() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
        entityManager.flush();

        boolean reclaimed = jobAnalysisRepository.reclaimStaleClaim(vacancyId, NOW.plusSeconds(10), NOW.minusSeconds(1));

        assertThat(reclaimed).isFalse();
    }

    @Test
    void reclaimStaleClaim_staleClaim_isReclaimedAndUpdatedAtAdvances() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
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
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
        entityManager.flush();

        boolean released = jobAnalysisRepository.releaseClaim(vacancyId);
        entityManager.flush();
        entityManager.clear();

        assertThat(released).isTrue();
        assertThat(countByVacancyId(vacancyId)).isEqualTo(0);

        Optional<JobAnalysisEntity> reclaimed = jobAnalysisRepository.claimIfAbsent(vacancyId, NOW.plusSeconds(1));
        assertThat(reclaimed).isPresent();
    }

    @Test
    void releaseClaim_completedAnalysis_doesNotDeleteIt() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.persist(vacancyId, analysis());
        entityManager.flush();

        boolean released = jobAnalysisRepository.releaseClaim(vacancyId);

        assertThat(released).isFalse();
        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isPresent();
    }

    @Test
    void findCompletedByVacancyId_inProgressClaim_returnsEmpty() {
        UUID vacancyId = persistVacancy().getId();
        jobAnalysisRepository.claimIfAbsent(vacancyId, NOW);
        entityManager.flush();

        assertThat(jobAnalysisRepository.findCompletedByVacancyId(vacancyId)).isEmpty();
    }

    @Test
    void persist_plainInsertPath_storesCompletedAnalysis() {
        UUID vacancyId = persistVacancy().getId();

        PersistedJobAnalysis persisted = jobAnalysisRepository.persist(vacancyId, analysis());
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
                    return transactionTemplate.execute(status -> jobAnalysisRepository.claimIfAbsent(vacancyId, NOW).isPresent());
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

    private long countByVacancyId(UUID vacancyId) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM job_analysis WHERE vacancy_id = ?1")
                        .setParameter(1, vacancyId)
                        .getSingleResult())
                .longValue();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job/" + UUID.randomUUID())
                .build());
        entityManager.flush();
        return vacancy;
    }

    private JobAnalysis analysis() {
        return new JobAnalysis(85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), "Good match");
    }
}
