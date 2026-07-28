package com.darya.jobassistant.vacancies.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.mapper.CompanyMapper;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.service.VacancyCreationService;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import java.net.URI;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
class VacancyRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void saveIfAbsent_newUrl_insertsAndReturnsInsertedWithDurableUuid() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
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
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
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
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
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

    @Test
    void saveIfAbsent_locationRemoteModeAndSalaryText_arePersistedAndReturnedVerbatim() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String url = uniqueUrl();
        Vacancy candidate = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Build backend services")
                .url(url)
                .location("Warszawa/ Centrum")
                .remoteMode(RemotePolicy.HYBRID)
                .salaryText("120-175 PLN netto/h +VAT")
                .source("manual_telegram")
                .build();

        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(candidate);

        assertThat(result.status()).isEqualTo(VacancyPersistenceResult.Status.INSERTED);
        assertThat(result.vacancy().getLocation()).isEqualTo("Warszawa/ Centrum");
        assertThat(result.vacancy().getRemoteMode()).isEqualTo(RemotePolicy.HYBRID);
        assertThat(result.vacancy().getSalaryText()).isEqualTo("120-175 PLN netto/h +VAT");
    }

    @Test
    void saveIfAbsent_persistsCanonicalUrlAlongsideOriginalUrlUnchanged() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String rawUrl = "HTTPS://EXAMPLE.COM:443/jobs/" + UUID.randomUUID() + "/?utm_source=linkedin#top";

        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, rawUrl));

        assertThat(result.vacancy().getUrl()).isEqualTo(rawUrl);
        assertThat(result.vacancy().getCanonicalUrl()).isEqualTo(canonicalize(rawUrl).value());
        assertThat(result.vacancy().getCanonicalUrl()).isNotEqualTo(rawUrl);
    }

    @Test
    void findByCanonicalUrl_findsInsertedVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String rawUrl = uniqueUrl();
        VacancyPersistenceResult inserted = vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, rawUrl));

        Optional<Vacancy> found = vacancyRepository.findByCanonicalUrl(canonicalize(rawUrl));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(inserted.vacancy().getId());
    }

    @Test
    void findByCanonicalUrl_legacyRowWithNullCanonicalUrl_isNeverMatched() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String rawUrl = uniqueUrl();
        Vacancy legacyRow = vacancy(company, rawUrl);
        legacyRow.setCanonicalUrl(null);
        vacancyRepository.save(legacyRow);

        Optional<Vacancy> found = vacancyRepository.findByCanonicalUrl(canonicalize(rawUrl));

        assertThat(found).isEmpty();
    }

    @Test
    void multipleLegacyNullCanonicalUrls_areAllowed() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());

        Vacancy first = vacancy(company, uniqueUrl());
        first.setCanonicalUrl(null);
        Vacancy second = vacancy(company, uniqueUrl());
        second.setCanonicalUrl(null);

        assertThat(vacancyRepository.save(first).getId()).isNotNull();
        assertThat(vacancyRepository.save(second).getId()).isNotNull();
    }

    @Test
    void saveIfAbsent_twoDistinctCanonicalUrls_bothInserted() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());

        VacancyPersistenceResult first = vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, uniqueUrl()));
        VacancyPersistenceResult second = vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, uniqueUrl()));

        assertThat(first.isInserted()).isTrue();
        assertThat(second.isInserted()).isTrue();
    }

    /**
     * Proves the {@code uk_vacancy_canonical_url} partial unique index itself, independent of any
     * application-level pre-check: two genuinely different {@code url} values (so {@code
     * uk_vacancy_url}'s {@code ON CONFLICT} never engages) that share a canonical identity must
     * still be rejected by the database when inserted directly through {@link
     * VacancyRepository#saveIfAbsent}, which does not catch this violation itself (see its
     * javadoc) - {@link VacancyCreationService} is what a real caller uses to get the friendlier,
     * translated outcome (covered separately below).
     */
    @Test
    void saveIfAbsent_duplicateCanonicalUrlWithDifferentRawUrl_violatesPartialUniqueIndex() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String firstUrl = "https://example.com/jobs/" + suffix;
        String secondUrl = "https://example.com/jobs/" + suffix + "?utm_source=linkedin";
        assertThat(canonicalize(firstUrl)).isEqualTo(canonicalize(secondUrl));

        vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, firstUrl));

        assertThatThrownBy(() -> vacancyRepository.saveIfAbsent(vacancyWithCanonicalUrl(company, secondUrl)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void vacancyCreationService_urlVariantsDifferingByCosmeticFormatting_resolveToOneVacancy() {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String canonicalForm = "https://example.com/jobs/" + suffix;
        // Tracking parameter, uppercase scheme/host, default port, fragment, non-root trailing slash.
        String messyVariant = "HTTPS://EXAMPLE.COM:443/jobs/" + suffix + "/?utm_source=linkedin#details";

        VacancyCreationResult first = vacancyCreationService.createIfAbsent(command(company.getName(), canonicalForm));
        VacancyCreationResult second = vacancyCreationService.createIfAbsent(command(company.getName(), messyVariant));

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.vacancy().getId()).isEqualTo(first.vacancy().getId());
        // Scoped to this test's own canonical value, not the whole table: VacancyCreationService
        // commits each creation attempt in its own isolated transaction (by design - see its
        // javadoc), so @DataJpaTest's per-method rollback does not undo rows earlier test methods
        // in this class already committed.
        assertThat(vacancyRepository.findAll().stream()
                .filter(v -> canonicalize(canonicalForm).value().equals(v.getCanonicalUrl()))
                .count()).isEqualTo(1);
    }

    @Test
    void vacancyCreationService_urlsDifferingByMeaningfulQueryParameter_remainDistinct() {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String english = "https://example.com/jobs/" + suffix + "?language=en";
        String polish = "https://example.com/jobs/" + suffix + "?language=pl";

        VacancyCreationResult first = vacancyCreationService.createIfAbsent(command(company.getName(), english));
        VacancyCreationResult second = vacancyCreationService.createIfAbsent(command(company.getName(), polish));

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isTrue();
        assertThat(second.vacancy().getId()).isNotEqualTo(first.vacancy().getId());
    }

    @Test
    void vacancyCreationService_httpAndHttps_remainDistinct() {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();

        VacancyCreationResult httpResult =
                vacancyCreationService.createIfAbsent(command(company.getName(), "http://example.com/jobs/" + suffix));
        VacancyCreationResult httpsResult =
                vacancyCreationService.createIfAbsent(command(company.getName(), "https://example.com/jobs/" + suffix));

        assertThat(httpResult.newlyCreated()).isTrue();
        assertThat(httpsResult.newlyCreated()).isTrue();
        assertThat(httpsResult.vacancy().getId()).isNotEqualTo(httpResult.vacancy().getId());
    }

    /**
     * The real-database counterpart of {@code VacancyCreationServiceTest}'s equivalent unit
     * tests: proves the {@code uk_vacancy_canonical_url} violation this project's actual
     * PostgreSQL/Hibernate stack raises for two genuinely concurrent callers is correctly
     * recognized and both futures complete cleanly - neither with a raw {@code
     * DataIntegrityViolationException}, {@code SQLException}, {@code
     * UnexpectedRollbackException}, nor a transaction-aborted error - because the losing
     * insert's failure is isolated to its own {@code REQUIRES_NEW} transaction and rolled back
     * before winner resolution ever runs.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void vacancyCreationService_concurrentCallsWithDifferentRawUrlsSameCanonical_exactlyOneRowCreated() throws Exception {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
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
                return vacancyCreationService.createIfAbsent(command(company.getName(), firstUrl));
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return vacancyCreationService.createIfAbsent(command(company.getName(), secondUrl));
            }));

            // Both futures must complete without throwing at all - get() would rethrow (wrapped
            // in ExecutionException) any DataIntegrityViolationException, SQLException,
            // UnexpectedRollbackException, or transaction-aborted error that leaked out.
            List<VacancyCreationResult> results = new ArrayList<>();
            for (Future<VacancyCreationResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long createdCount = results.stream().filter(VacancyCreationResult::newlyCreated).count();
            long alreadyExistsCount = results.size() - createdCount;
            assertThat(createdCount).isEqualTo(1);
            assertThat(alreadyExistsCount).isEqualTo(1);
            assertThat(results.get(0).vacancy().getId()).isEqualTo(results.get(1).vacancy().getId());
            assertThat(vacancyRepository.findAll().stream()
                    .filter(v -> canonicalize(firstUrl).value().equals(v.getCanonicalUrl()))
                    .count()).isEqualTo(1);

            // A subsequent lookup in a fresh call succeeds - the winning row is fully committed
            // and visible, not left in some half-finished state by the loser's rollback.
            Optional<Vacancy> reloaded = vacancyRepository.findById(results.get(0).vacancy().getId());
            assertThat(reloaded).isPresent();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Directly demonstrates the bug this correction fixes: wraps {@code createIfAbsent} the same
     * way {@code VacancyImportReviewService} does (an outer {@code REQUIRES_NEW} transaction
     * around the whole call), forces a canonical conflict inside it, and then performs another
     * write in that *same* outer transaction afterward - proving the outer transaction was never
     * poisoned by the isolated insert's rollback, unlike before this correction (where the
     * conflict and the outer transaction shared one physical transaction, and the outer
     * transaction would have been left aborted).
     */
    @Test
    void vacancyCreationService_canonicalConflict_doesNotPoisonCallersOuterTransaction() {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        vacancyCreationService.createIfAbsent(command(company.getName(), "https://example.com/jobs/" + suffix));

        String conflictingUrl = "https://example.com/jobs/" + suffix + "?utm_source=linkedin";
        VacancyCreationResult resultAfterConflictInsideOuterTransaction = outerTransaction.execute(status -> {
            VacancyCreationResult conflictResult = vacancyCreationService.createIfAbsent(command(company.getName(), conflictingUrl));
            // If the isolated-insert rollback had poisoned this outer transaction, this second,
            // unrelated write would fail here with a transaction-aborted error.
            companyRepository.save(Company.builder().name("Another Co - " + suffix).build());
            return conflictResult;
        });

        assertThat(resultAfterConflictInsideOuterTransaction.newlyCreated()).isFalse();
        assertThat(companyRepository.findAll().stream().anyMatch(c -> c.getName().equals("Another Co - " + suffix))).isTrue();
    }

    /**
     * A real database constraint/data violation unrelated to {@code uk_vacancy_canonical_url}
     * must still propagate as a {@link DataIntegrityViolationException} rather than being
     * swallowed as a canonical duplicate. {@code company_id}/{@code title NOT NULL} can no longer
     * be triggered through the corrected, valid-command API surface (that is the point of this
     * correction: {@link VacancyCreationCommand} itself requires a non-blank company name and
     * title, and company resolution/creation always succeeds inside the same transaction) - a
     * {@code currency} value exceeding its {@code VARCHAR(10)} column width is used instead as a
     * realistic, still-genuinely-unrelated database-level violation.
     */
    @Test
    void vacancyCreationService_unrelatedIntegrityViolation_isPropagatedNotSwallowed() {
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService(), transactionManager);
        VacancyCreationCommand oversizedCurrency = new VacancyCreationCommand(
                "Acme-" + UUID.randomUUID(), "Backend Engineer", "Build backend services", uniqueUrl(),
                null, null, null, null, "THIS-CURRENCY-CODE-IS-WAY-TOO-LONG", null, "remoteok", null);

        assertThatThrownBy(() -> vacancyCreationService.createIfAbsent(oversizedCurrency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CanonicalVacancyUrl canonicalize(String rawUrl) {
        return VacancyUrlCanonicalizer.canonicalize(URI.create(rawUrl));
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

    /**
     * {@link VacancyRepository#saveIfAbsent} persists {@code canonicalUrl} exactly as given
     * rather than computing it (that is {@code VacancyCreationService}'s job - see its javadoc),
     * so tests exercising {@code saveIfAbsent} directly must set it themselves.
     */
    private Vacancy vacancyWithCanonicalUrl(Company company, String url) {
        Vacancy candidate = vacancy(company, url);
        candidate.setCanonicalUrl(canonicalize(url).value());
        return candidate;
    }

    private String uniqueUrl() {
        return "https://example.com/job-" + UUID.randomUUID();
    }

    /** A real (not mocked) {@link CompanyService} backed by the real, Testcontainers-connected {@link #companyRepository}. */
    private CompanyService companyService() {
        return new CompanyService(companyRepository, new CompanyMapper());
    }

    private VacancyCreationCommand command(String companyName, String url) {
        return new VacancyCreationCommand(
                companyName, "Backend Engineer", "Build backend services", url,
                null, null, null, null, null, null, "remoteok", null);
    }
}
