package com.darya.jobassistant.vacancies.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class VacancyRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void saveIfAbsent_newUrl_insertsAndReturnsInsertedWithDurableUuid() {
        Company company = companyRepository.save(Company.builder().name("Acme").build());
        String url = uniqueUrl();

        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(vacancy(company, url));

        assertThat(result.status()).isEqualTo(VacancyPersistenceResult.Status.INSERTED);
        assertThat(result.vacancy()).isNotNull();
        assertThat(result.vacancy().getId()).isNotNull();
        assertThat(result.vacancy().getUrl()).isEqualTo(url);
        assertThat(result.vacancy().getTitle()).isEqualTo("Backend Engineer");
    }

    @Test
    void saveIfAbsent_sameUrlCalledTwice_secondCallReportsAlreadyExistsAndOnlyOneRowExists() {
        Company company = companyRepository.save(Company.builder().name("Acme").build());
        String url = uniqueUrl();

        VacancyPersistenceResult first = vacancyRepository.saveIfAbsent(vacancy(company, url));
        VacancyPersistenceResult second = vacancyRepository.saveIfAbsent(vacancy(company, url));

        assertThat(first.status()).isEqualTo(VacancyPersistenceResult.Status.INSERTED);
        assertThat(second.status()).isEqualTo(VacancyPersistenceResult.Status.ALREADY_EXISTS);
        assertThat(second.vacancy()).isNull();
        assertThat(countByUrl(url)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void saveIfAbsent_concurrentCallsWithSameUrl_exactlyOneInsertedAndOneAlreadyExists() throws Exception {
        Company company = companyRepository.save(Company.builder().name("Acme").build());
        String url = uniqueUrl();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<VacancyPersistenceResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return vacancyRepository.saveIfAbsent(vacancy(company, url));
                }));
            }

            List<VacancyPersistenceResult> results = new ArrayList<>();
            for (Future<VacancyPersistenceResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long insertedCount = results.stream().filter(VacancyPersistenceResult::isInserted).count();
            long alreadyExistsCount = results.size() - insertedCount;

            assertThat(insertedCount).isEqualTo(1);
            assertThat(alreadyExistsCount).isEqualTo(1);
            assertThat(countByUrl(url)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private long countByUrl(String url) {
        return vacancyRepository.findAll().stream().filter(v -> url.equals(v.getUrl())).count();
    }

    private Vacancy vacancy(Company company, String url) {
        return Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Build backend services")
                .url(url)
                .source("remoteok")
                .build();
    }

    private String uniqueUrl() {
        return "https://example.com/job-" + UUID.randomUUID();
    }
}
