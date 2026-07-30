package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.mapper.CompanyMapper;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskStatus;
import com.darya.jobassistant.vacancyrecommendation.entity.VacancyRecommendationTaskEntity;
import com.darya.jobassistant.vacancyrecommendation.repository.VacancyRecommendationTaskRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL counterpart of {@code VacancyIngestionServiceTest}'s mocked {@code
 * persistDiscovered} tests: proves the atomic Vacancy+{@code VacancyRecommendationTask}
 * registration this project's actual schema/constraints enforce, including under a genuine
 * concurrent canonical-URL race between two independent {@link VacancyIngestionService}
 * instances (mirroring {@code VacancyRepositoryTest}'s
 * {@code vacancyCreationService_concurrentCallsWithDifferentRawUrlsSameCanonical_exactlyOneRowCreated}
 * convention).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class VacancyIngestionServicePersistDiscoveredRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRecommendationTaskRepository vacancyRecommendationTaskRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistDiscovered_created_commitsVacancyAndExactlyOnePendingTaskTogether() {
        VacancyIngestionService service = newService();
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());

        VacancyCreationResult result = service.persistDiscovered(command(company.getName(), uniqueUrl()));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.newlyCreated()).isTrue();
        UUID vacancyId = result.vacancy().getId();
        Optional<VacancyRecommendationTaskEntity> task = vacancyRecommendationTaskRepository.findByVacancyId(vacancyId);
        assertThat(task).isPresent();
        assertThat(task.get().getStatus()).isEqualTo(VacancyRecommendationTaskStatus.PENDING);
        assertThat(task.get().getAttemptCount()).isZero();
        assertThat(vacancyRepository.findById(vacancyId)).isPresent();
    }

    @Test
    void persistDiscovered_alreadyExists_createsNoTaskForTheExistingVacancy() {
        VacancyIngestionService service = newService();
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String url = uniqueUrl();
        VacancyCreationResult first = service.persistDiscovered(command(company.getName(), url));
        entityManager.flush();
        entityManager.clear();

        VacancyCreationResult second = service.persistDiscovered(command(company.getName(), url));
        entityManager.flush();

        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.vacancy().getId()).isEqualTo(first.vacancy().getId());
        assertThat(vacancyRecommendationTaskRepository.findByVacancyId(first.vacancy().getId())).isPresent();
        // Exactly one task exists in total - the second call never inserted a duplicate.
        assertThat(countTasksForVacancy(first.vacancy().getId())).isEqualTo(1);
    }

    /**
     * The core atomicity proof: if a genuinely concurrent second caller wins the canonical-URL
     * race for the same logical vacancy, exactly one {@code vacancy_recommendation_task} row
     * exists afterward, never zero (task lost) and never two (duplicate task for one vacancy,
     * which {@code uk_vacancy_recommendation_task_vacancy} would reject anyway).
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistDiscovered_concurrentCallsWithDifferentRawUrlsSameCanonical_exactlyOneTaskCreated() throws Exception {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String firstUrl = "https://example.com/jobs/" + suffix;
        String secondUrl = "https://example.com/jobs/" + suffix + "?utm_source=linkedin";
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<VacancyCreationResult>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                barrier.await();
                return newService().persistDiscovered(command(company.getName(), firstUrl));
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return newService().persistDiscovered(command(company.getName(), secondUrl));
            }));

            List<VacancyCreationResult> results = new ArrayList<>();
            for (Future<VacancyCreationResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long createdCount = results.stream().filter(VacancyCreationResult::newlyCreated).count();
            assertThat(createdCount).isEqualTo(1);
            UUID vacancyId = results.get(0).vacancy().getId();
            assertThat(results.get(1).vacancy().getId()).isEqualTo(vacancyId);
            assertThat(countTasksForVacancy(vacancyId)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private long countTasksForVacancy(UUID vacancyId) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM vacancy_recommendation_task WHERE vacancy_id = ?1")
                        .setParameter(1, vacancyId)
                        .getSingleResult())
                .longValue();
    }

    private String uniqueUrl() {
        return "https://example.com/job-" + UUID.randomUUID();
    }

    private VacancyCreationCommand command(String companyName, String url) {
        return new VacancyCreationCommand(
                companyName, "Backend Engineer", "Build backend services", url,
                null, null, null, null, null, null, "remoteok", null);
    }

    /** A fresh, production-shaped {@link VacancyIngestionService} backed by the real, Testcontainers-connected beans. */
    private VacancyIngestionService newService() {
        CompanyService companyService = new CompanyService(companyRepository, new CompanyMapper());
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService);
        return new VacancyIngestionService(
                vacancyCreationService, jobOffer -> true, vacancyRecommendationTaskRepository, CLOCK, transactionManager);
    }
}
