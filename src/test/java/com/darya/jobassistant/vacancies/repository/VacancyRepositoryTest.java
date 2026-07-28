package com.darya.jobassistant.vacancies.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.mapper.CompanyMapper;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.service.CanonicalVacancyCreationConflictException;
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
                .canonicalUrl(canonicalize(url).value())
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

        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(vacancy(company, rawUrl));

        assertThat(result.vacancy().getUrl()).isEqualTo(rawUrl);
        assertThat(result.vacancy().getCanonicalUrl()).isEqualTo(canonicalize(rawUrl).value());
        assertThat(result.vacancy().getCanonicalUrl()).isNotEqualTo(rawUrl);
    }

    @Test
    void findByCanonicalUrl_findsInsertedVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String rawUrl = uniqueUrl();
        VacancyPersistenceResult inserted = vacancyRepository.saveIfAbsent(vacancy(company, rawUrl));

        Optional<Vacancy> found = vacancyRepository.findByCanonicalUrl(canonicalize(rawUrl));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(inserted.vacancy().getId());
    }

    /**
     * Since Sprint 8 Step 4B2D (migration V13), {@code canonical_url} is {@code NOT NULL} at the
     * database level - a "legacy null canonical_url row" (the pre-V13 scenario {@code
     * VacancyCanonicalUrlAuditRepositoryTest}/{@code VacancyCanonicalUrlBackfillRepositoryTest}
     * still cover, pinned to the V12 schema those tools are meant for) can no longer exist in any
     * database that has run this migration. This test - and {@code
     * saveIfAbsent_duplicateCanonicalUrlWithDifferentRawUrl_violatesUniqueIndex} below - replace
     * the old "legacy null rows are tolerated" tests that used to live here.
     */
    @Test
    void saveIfAbsent_nullCanonicalUrl_violatesNotNullConstraint() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        Vacancy candidate = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Build backend services")
                .url(uniqueUrl())
                .canonicalUrl(null)
                .source("remoteok")
                .build();

        assertThatThrownBy(() -> vacancyRepository.saveIfAbsent(candidate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveIfAbsent_twoDistinctCanonicalUrls_bothInserted() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());

        VacancyPersistenceResult first = vacancyRepository.saveIfAbsent(vacancy(company, uniqueUrl()));
        VacancyPersistenceResult second = vacancyRepository.saveIfAbsent(vacancy(company, uniqueUrl()));

        assertThat(first.isInserted()).isTrue();
        assertThat(second.isInserted()).isTrue();
    }

    /**
     * Proves the {@code uk_vacancy_canonical_url} unique index itself, independent of any
     * application-level pre-check: two genuinely different {@code url} values (so {@code
     * uk_vacancy_url}'s {@code ON CONFLICT} never engages) that share a canonical identity must
     * still be rejected by the database when inserted directly through {@link
     * VacancyRepository#saveIfAbsent}, which does not catch this violation itself (see its
     * javadoc) - {@link VacancyCreationService} is what a real caller uses to get the friendlier,
     * translated outcome (covered separately below). Since Sprint 8 Step 4B2D (V13) this index is
     * a full (non-partial) unique index rather than the original V12 partial one, but the
     * assertion here - a duplicate is rejected - is unaffected either way.
     */
    @Test
    void saveIfAbsent_duplicateCanonicalUrlWithDifferentRawUrl_violatesUniqueIndex() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String firstUrl = "https://example.com/jobs/" + suffix;
        String secondUrl = "https://example.com/jobs/" + suffix + "?utm_source=linkedin";
        assertThat(canonicalize(firstUrl)).isEqualTo(canonicalize(secondUrl));

        vacancyRepository.saveIfAbsent(vacancy(company, firstUrl));

        assertThatThrownBy(() -> vacancyRepository.saveIfAbsent(vacancy(company, secondUrl)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * {@code VacancyCreationService.isCanonicalUrlConflict} classifies a canonical-URL race by
     * this exact index name (see its javadoc) - this test is the real-Postgres proof that the
     * name PostgreSQL/Hibernate actually reports for a violation matches what that classification
     * depends on, independent of any application-level translation.
     */
    @Test
    void saveIfAbsent_duplicateCanonicalUrl_exposesTheExpectedIndexNameInTheRootCause() {
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String firstUrl = "https://example.com/jobs/" + suffix;
        String secondUrl = "https://example.com/jobs/" + suffix + "?utm_source=linkedin";

        vacancyRepository.saveIfAbsent(vacancy(company, firstUrl));

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> vacancyRepository.saveIfAbsent(vacancy(company, secondUrl)));

        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_vacancy_canonical_url");
    }

    @Test
    void vacancyCreationService_urlVariantsDifferingByCosmeticFormatting_resolveToOneVacancy() {
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String canonicalForm = "https://example.com/jobs/" + suffix;
        // Tracking parameter, uppercase scheme/host, default port, fragment, non-root trailing slash.
        String messyVariant = "HTTPS://EXAMPLE.COM:443/jobs/" + suffix + "/?utm_source=linkedin#details";

        // Each call owns and commits its own transaction, exactly like a real caller
        // (VacancyIngestionService/VacancyImportReviewService) would - VacancyCreationService
        // itself no longer opens one (see its javadoc).
        VacancyCreationResult first = createInOwnTransaction(vacancyCreationService, command(company.getName(), canonicalForm));
        VacancyCreationResult second = createInOwnTransaction(vacancyCreationService, command(company.getName(), messyVariant));

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isFalse();
        assertThat(second.vacancy().getId()).isEqualTo(first.vacancy().getId());
        // Scoped to this test's own canonical value, not the whole table: each call above
        // committed independently and durably, so @DataJpaTest's per-method rollback does not
        // undo rows earlier test methods in this class already committed.
        assertThat(vacancyRepository.findAll().stream()
                .filter(v -> canonicalize(canonicalForm).value().equals(v.getCanonicalUrl()))
                .count()).isEqualTo(1);
    }

    @Test
    void vacancyCreationService_urlsDifferingByMeaningfulQueryParameter_remainDistinct() {
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();
        String english = "https://example.com/jobs/" + suffix + "?language=en";
        String polish = "https://example.com/jobs/" + suffix + "?language=pl";

        VacancyCreationResult first = createInOwnTransaction(vacancyCreationService, command(company.getName(), english));
        VacancyCreationResult second = createInOwnTransaction(vacancyCreationService, command(company.getName(), polish));

        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.newlyCreated()).isTrue();
        assertThat(second.vacancy().getId()).isNotEqualTo(first.vacancy().getId());
    }

    @Test
    void vacancyCreationService_httpAndHttps_remainDistinct() {
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
        Company company = companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
        String suffix = UUID.randomUUID().toString();

        VacancyCreationResult httpResult =
                createInOwnTransaction(vacancyCreationService, command(company.getName(), "http://example.com/jobs/" + suffix));
        VacancyCreationResult httpsResult =
                createInOwnTransaction(vacancyCreationService, command(company.getName(), "https://example.com/jobs/" + suffix));

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
     * UnexpectedRollbackException}, nor a transaction-aborted error. Each thread owns its own
     * {@code REQUIRES_NEW} transaction per attempt and retries exactly once on a {@link
     * CanonicalVacancyCreationConflictException} - the same pattern {@code
     * VacancyIngestionService} and {@code VacancyImportReviewService} use in production, now that
     * {@code VacancyCreationService} itself never resolves the winner inside a failed transaction.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void vacancyCreationService_concurrentCallsWithDifferentRawUrlsSameCanonical_exactlyOneRowCreated() throws Exception {
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
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
                return createWithRetry(vacancyCreationService, command(company.getName(), firstUrl));
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return createWithRetry(vacancyCreationService, command(company.getName(), secondUrl));
            }));

            // Both futures must complete without throwing at all - get() would rethrow (wrapped
            // in ExecutionException) any DataIntegrityViolationException, SQLException,
            // UnexpectedRollbackException, or transaction-aborted error that leaked out of the
            // retry helper.
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
     * The core proof this whole correction exists for: if a caller's transaction creates a
     * {@code Vacancy}/{@code Company} and then something *else* in that same transaction fails
     * (here simulated with a direct {@code setRollbackOnly()}, standing in for e.g. a losing
     * conditional session-completion update in {@code VacancyImportReviewService}), no orphan
     * {@code Vacancy} or {@code Company} survives - both roll back together, atomically, because
     * {@link VacancyCreationService} no longer commits independently of its caller.
     */
    @Test
    void vacancyCreationService_outerTransactionRolledBackAfterCreation_leavesNoOrphanVacancyOrCompany() {
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
        String companyName = "Acme-" + UUID.randomUUID();
        String url = uniqueUrl();
        TransactionTemplate outerTransaction = requiresNewTransaction();

        VacancyCreationResult result = outerTransaction.execute(status -> {
            VacancyCreationResult created = vacancyCreationService.createIfAbsent(command(companyName, url));
            status.setRollbackOnly();
            return created;
        });

        assertThat(result.newlyCreated()).isTrue();
        assertThat(vacancyRepository.findByCanonicalUrl(canonicalize(url))).isEmpty();
        assertThat(companyRepository.findByNameIgnoreCase(companyName)).isEmpty();
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
        VacancyCreationService vacancyCreationService = new VacancyCreationService(vacancyRepository, companyService());
        VacancyCreationCommand oversizedCurrency = new VacancyCreationCommand(
                "Acme-" + UUID.randomUUID(), "Backend Engineer", "Build backend services", uniqueUrl(),
                null, null, null, null, "THIS-CURRENCY-CODE-IS-WAY-TOO-LONG", null, "remoteok", null);

        assertThatThrownBy(() -> createInOwnTransaction(vacancyCreationService, oversizedCurrency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CanonicalVacancyUrl canonicalize(String rawUrl) {
        return VacancyUrlCanonicalizer.canonicalize(URI.create(rawUrl));
    }

    private long countByUrl(String url) {
        return vacancyRepository.findAll().stream().filter(v -> url.equals(v.getUrl())).count();
    }

    /**
     * {@link VacancyRepository#saveIfAbsent} persists {@code canonicalUrl} exactly as given
     * rather than computing it (that is {@code VacancyCreationService}'s job - see its javadoc),
     * so this always derives one from {@code url} itself; since Sprint 8 Step 4B2D (V13),
     * {@code canonical_url} is {@code NOT NULL} at the database level, so no test helper can build
     * a persistable {@code Vacancy} without one. A test that specifically needs to prove {@code
     * NOT NULL} rejection builds its own candidate with an explicit {@code canonicalUrl(null)}
     * instead of using this helper.
     */
    private Vacancy vacancy(Company company, String url) {
        return Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Build backend services")
                .url(url)
                .canonicalUrl(canonicalize(url).value())
                .source("remoteok")
                .build();
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

    /**
     * Mirrors how every real production caller uses {@link VacancyCreationService}: it never
     * opens its own transaction (see its javadoc), so a single production-equivalent call always
     * owns and commits one {@code REQUIRES_NEW} transaction of its own.
     */
    private VacancyCreationResult createInOwnTransaction(VacancyCreationService vacancyCreationService, VacancyCreationCommand command) {
        return requiresNewTransaction().execute(status -> vacancyCreationService.createIfAbsent(command));
    }

    /**
     * Mirrors {@code VacancyIngestionService#createWithRetry}: one {@code REQUIRES_NEW}
     * transaction per attempt, retried exactly once - in a fresh transaction - if the first
     * attempt loses a canonical-URL race.
     */
    private VacancyCreationResult createWithRetry(VacancyCreationService vacancyCreationService, VacancyCreationCommand command) {
        TransactionTemplate perAttemptTransaction = requiresNewTransaction();
        try {
            return perAttemptTransaction.execute(status -> vacancyCreationService.createIfAbsent(command));
        } catch (CanonicalVacancyCreationConflictException firstConflict) {
            return perAttemptTransaction.execute(status -> vacancyCreationService.createIfAbsent(command));
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
