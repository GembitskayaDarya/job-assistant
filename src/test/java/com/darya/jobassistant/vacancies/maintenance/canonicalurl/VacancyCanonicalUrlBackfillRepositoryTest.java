package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL counterpart of {@link VacancyCanonicalUrlBackfillServiceTest}: proves {@link
 * VacancyCanonicalUrlBackfillService#apply()}'s atomicity, the {@code
 * uk_vacancy_canonical_url}-safe write pattern, and idempotency against this project's actual
 * schema, complementing {@code VacancyCanonicalUrlMigrationTest} (which exercises V12 itself) and
 * {@code VacancyRepositoryTest} (which this class leaves untouched).
 *
 * <p>Every test method disables {@code @DataJpaTest}'s own per-method ambient transaction ({@code
 * Propagation.NOT_SUPPORTED}): {@link VacancyCanonicalUrlBackfillService#apply()} always opens its
 * own {@code REQUIRES_NEW} transaction, which would otherwise suspend - and be unable to see -
 * setup data this test saved into a still-uncommitted ambient transaction, exactly the class of bug
 * Sprint 8 Step 4B1 fixed in {@code VacancyCreationService} itself.
 *
 * <p>Pinned to Flyway target {@code 12} ({@code spring.flyway.target=12}), not {@code latest}: since
 * Sprint 8 Step 4B2D (migration V13), {@code canonical_url} is {@code NOT NULL} at the database
 * level, so a "legacy row with {@code canonical_url IS NULL}" - the entire premise of this backfill
 * tool and every test in this class - can no longer exist in a database migrated past V12. This
 * class specifically exercises the tool an operator runs <em>against a V12 database that has not
 * yet been backfilled</em>, so its own test database must stay at V12 to match that real scenario.
 *
 * <p>{@code hibernate.ddl-auto} is overridden to {@code none} here: {@code @DataJpaTest} validates
 * every {@code @Entity} in the app against the actual schema, not just the ones this class exercises,
 * so once any later migration adds a column to an unrelated entity (e.g. {@code job_analysis}), a
 * V12-pinned database would otherwise fail Hibernate's schema validation for a mismatch this class
 * has no interest in.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {"spring.flyway.target=12", "spring.jpa.hibernate.ddl-auto=none"})
class VacancyCanonicalUrlBackfillRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * With the ambient transaction disabled ({@code NOT_SUPPORTED}), nothing here rolls back
     * between test methods the way {@code @DataJpaTest} normally would - every {@code save} is a
     * real, durable commit. {@link VacancyCanonicalUrlLegacyPlanner} scans the *whole* table, so a
     * row one test deliberately leaves as legacy (e.g. the blocked-APPLY test's invalid row) would
     * otherwise poison every other test's plan. Clearing the table after each method keeps every
     * test's legacy rows scoped to itself, regardless of execution order.
     */
    @AfterEach
    void cleanUpVacancyTable() {
        vacancyRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void apply_fillsEverySafeCanonicalUrl_andPreservesOriginalUrl() {
        Company company = company();
        String urlOne = uniqueUrl();
        String urlTwo = "HTTPS://EXAMPLE.COM:443/jobs/" + UUID.randomUUID() + "/?utm_source=linkedin";
        Vacancy first = legacyVacancy(company, urlOne);
        Vacancy second = legacyVacancy(company, urlTwo);
        vacancyRepository.save(first);
        vacancyRepository.save(second);

        VacancyCanonicalUrlBackfillResult result = backfillService(500).apply();

        assertThat(result.committed()).isTrue();
        assertThat(result.updatedRows()).isEqualTo(2);
        Vacancy reloadedFirst = vacancyRepository.findById(first.getId()).orElseThrow();
        Vacancy reloadedSecond = vacancyRepository.findById(second.getId()).orElseThrow();
        assertThat(reloadedFirst.getUrl()).isEqualTo(urlOne);
        assertThat(reloadedFirst.getCanonicalUrl()).isEqualTo(canonicalize(urlOne));
        assertThat(reloadedSecond.getUrl()).isEqualTo(urlTwo);
        assertThat(reloadedSecond.getCanonicalUrl()).isEqualTo(canonicalize(urlTwo));
    }

    @Test
    void apply_partialUniqueIndexRemainsValid_afterBackfill() {
        Company company = company();
        String url = uniqueUrl();
        vacancyRepository.save(legacyVacancy(company, url));
        backfillService(500).apply();

        // A second row whose canonical identity now matches the just-backfilled row must still be
        // rejected by uk_vacancy_canonical_url - the index is exercised against backfilled values
        // exactly as it already is against values VacancyCreationService writes.
        Vacancy conflicting = legacyVacancy(company, uniqueUrl());
        conflicting.setCanonicalUrl(canonicalize(url));
        assertThatThrownBy(() -> vacancyRepository.saveAndFlush(conflicting))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void apply_forcedBlocker_producesNoPartialWrites() {
        Company company = company();
        Vacancy safe = legacyVacancy(company, uniqueUrl());
        Vacancy invalid = legacyVacancy(company, "not a url");
        vacancyRepository.save(safe);
        vacancyRepository.save(invalid);

        assertThatThrownBy(() -> backfillService(500).apply())
                .isInstanceOf(VacancyCanonicalUrlBackfillBlockedException.class);

        assertThat(vacancyRepository.findById(safe.getId()).orElseThrow().getCanonicalUrl()).isNull();
        assertThat(vacancyRepository.findById(invalid.getId()).orElseThrow().getCanonicalUrl()).isNull();
    }

    @Test
    void apply_forcedConditionalUpdateMismatch_rollsBackTheCompleteRun() {
        Company company = company();
        Vacancy first = legacyVacancy(company, uniqueUrl());
        Vacancy second = legacyVacancy(company, uniqueUrl());
        vacancyRepository.save(first);
        vacancyRepository.save(second);

        // A plain Mockito mock with AdditionalAnswers.delegatesTo(vacancyRepository) as its default
        // answer - not Mockito.spy(vacancyRepository) - because spying directly on an injected
        // Spring Data JPA repository proxy conflicts with Spring Boot's own mock-resolution
        // machinery in this test slice. Every unstubbed call still reaches the real repository.
        VacancyRepository delegatingRepository =
                Mockito.mock(VacancyRepository.class, AdditionalAnswers.delegatesTo(vacancyRepository));
        UUID nonExistentId = UUID.randomUUID();
        // First call proceeds normally against the real, planned vacancy id. Every call after that
        // is redirected to an id that matches no row at all, forcing its conditional UPDATE to
        // affect exactly zero rows - simulating "the plan is no longer valid" deterministically,
        // without needing genuine thread concurrency (which, under REPEATABLE_READ, would surface
        // as a Postgres serialization failure rather than a clean zero-rows-affected result). A
        // single stateful answer (rather than chained thenAnswer(a).thenAnswer(b) calls) is used so
        // this holds regardless of exactly how many calls happen. doAnswer(...).when(mock) rather
        // than when(mock.method(...)) - the latter would itself invoke setCanonicalUrlIfNull once
        // (via the mock's delegatesTo default answer) just to record the stub, before any
        // transaction exists, failing with TransactionRequiredException.
        AtomicInteger callCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return vacancyRepository.setCanonicalUrlIfNull(invocation.getArgument(0), invocation.getArgument(1));
            }
            return vacancyRepository.setCanonicalUrlIfNull(nonExistentId, invocation.getArgument(1));
        }).when(delegatingRepository).setCanonicalUrlIfNull(any(), any());

        VacancyCanonicalUrlBackfillService service = new VacancyCanonicalUrlBackfillService(
                delegatingRepository, new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.APPLY, 500),
                transactionManager);

        assertThatThrownBy(service::apply).isInstanceOf(VacancyCanonicalUrlBackfillInvariantViolationException.class);

        // The whole transaction rolled back - including whichever update ran first and "succeeded".
        assertThat(vacancyRepository.findById(first.getId()).orElseThrow().getCanonicalUrl()).isNull();
        assertThat(vacancyRepository.findById(second.getId()).orElseThrow().getCanonicalUrl()).isNull();
    }

    @Test
    void apply_secondRun_isAnIdempotentSuccessfulNoOp() {
        Company company = company();
        vacancyRepository.save(legacyVacancy(company, uniqueUrl()));
        VacancyCanonicalUrlBackfillService service = backfillService(500);

        VacancyCanonicalUrlBackfillResult firstRun = service.apply();
        VacancyCanonicalUrlBackfillResult secondRun = service.apply();

        assertThat(firstRun.updatedRows()).isEqualTo(1);
        assertThat(secondRun.legacyRowsScanned()).isZero();
        assertThat(secondRun.plannedAssignments()).isZero();
        assertThat(secondRun.updatedRows()).isZero();
        assertThat(secondRun.committed()).isTrue();
    }

    @Test
    void dryRun_leavesLegacyRowsUnchanged() {
        Company company = company();
        String url = uniqueUrl();
        Vacancy legacy = legacyVacancy(company, url);
        vacancyRepository.save(legacy);

        VacancyCanonicalUrlBackfillResult result = backfillService(500).dryRun();

        assertThat(result.plannedAssignments()).isEqualTo(1);
        assertThat(result.committed()).isFalse();
        Optional<Vacancy> reloaded = vacancyRepository.findById(legacy.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getCanonicalUrl()).isNull();
        assertThat(reloaded.get().getUrl()).isEqualTo(url);
    }

    private VacancyCanonicalUrlBackfillService backfillService(int batchSize) {
        return new VacancyCanonicalUrlBackfillService(
                vacancyRepository, new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.APPLY, batchSize),
                transactionManager);
    }

    private String canonicalize(String rawUrl) {
        return VacancyUrlCanonicalizer.canonicalize(URI.create(rawUrl)).value();
    }

    private Company company() {
        return companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
    }

    private Vacancy legacyVacancy(Company company, String url) {
        return Vacancy.builder()
                .company(company).title("Backend Engineer").description("Build backend services")
                .url(url).canonicalUrl(null).source("remoteok").build();
    }

    private String uniqueUrl() {
        return "https://example.com/job-" + UUID.randomUUID();
    }
}
