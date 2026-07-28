package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import java.net.URI;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single production entry point for creating a new {@link Vacancy}. Every current and future
 * creation path (manual/guided import, automatic RemoteOK ingestion, and any future discovery
 * source) must go through this method rather than resolving a {@link Company} or calling {@link
 * VacancyRepository#saveIfAbsent} directly, so canonicalization, company resolution, and duplicate
 * handling are never duplicated or accidentally skipped by a new caller.
 *
 * <p>No Firecrawl-specific logic lives here - this class only knows about the provider-neutral
 * {@link VacancyUrlCanonicalizer}, {@link CompanyService}, and the {@link Vacancy} persistence
 * model.
 *
 * <h2>Why the creation attempt takes a command, not a pre-built entity</h2>
 *
 * An earlier version of this method accepted an already-built {@code Vacancy} (with its {@code
 * Company} already resolved by the caller). That caused a real production failure: a caller
 * (e.g. {@code VacancyIngestionService}, {@code VacancyImportReviewService}) would resolve/create
 * the {@code Company} in its own ambient or {@code newTransaction} scope, hand the resulting
 * (uncommitted) entity to this method, which would then suspend that transaction to run the
 * insert in its own {@code REQUIRES_NEW} transaction - a transaction that cannot see a row that
 * only exists in the now-suspended, uncommitted caller transaction, so the insert failed with a
 * {@code vacancy_company_id_fkey} violation. {@link VacancyCreationCommand} instead carries only
 * plain data (including the company's name, never a persisted entity), so company resolution can
 * happen here, inside the very same isolated transaction as the {@code Vacancy} insert - the two
 * always commit or roll back together.
 *
 * <h2>Why the insert attempt runs in its own transaction</h2>
 *
 * PostgreSQL aborts the entire physical transaction a statement error occurred in - catching the
 * resulting {@link DataIntegrityViolationException} in Java does not undo that at the database
 * level. Every other statement attempted on that same connection/transaction (including the
 * winner-resolution lookup below) would fail too, and - worse - so would anything else sharing
 * that transaction with this call. The whole creation attempt (company resolution, the recheck,
 * and the {@code Vacancy} insert) therefore runs in its own {@code PROPAGATION_REQUIRES_NEW}
 * transaction (via {@link #isolatedCreationTransaction}, matching the {@code
 * TransactionTemplate}-based isolation pattern already established in {@code
 * VacancyImportService}/{@code VacancyImportReviewService}): only that inner transaction is
 * aborted and rolled back on a conflict - including any {@code Company} it created - and the
 * caller's own transaction (if any) - suspended, never touched - resumes afterward exactly as
 * valid as before.
 */
@Slf4j
@Service
public class VacancyCreationService {

    /** Must match the index name in {@code V12__vacancy_canonical_url.sql}. */
    private static final String CANONICAL_URL_UNIQUE_INDEX_NAME = "uk_vacancy_canonical_url";

    private final VacancyRepository vacancyRepository;
    private final CompanyService companyService;
    private final TransactionTemplate isolatedCreationTransaction;

    public VacancyCreationService(VacancyRepository vacancyRepository, CompanyService companyService,
                                   PlatformTransactionManager transactionManager) {
        this.vacancyRepository = vacancyRepository;
        this.companyService = companyService;
        this.isolatedCreationTransaction = new TransactionTemplate(transactionManager);
        this.isolatedCreationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sequence: canonicalize -&gt; fast canonical lookup (a cheap, non-isolated read - short-
     * circuits the common already-exists case without ever touching {@link CompanyService} or
     * opening the isolated transaction) -&gt; attempt creation in an isolated transaction, which
     * rechecks novelty (another transaction may have committed since the fast lookup above)
     * before resolving/creating the {@link Company} and inserting the {@link Vacancy}. There is
     * still a race between the fast lookup and the isolated attempt; the database's {@code
     * uk_vacancy_canonical_url} partial unique index is the actual source of truth for novelty,
     * and a violation of it is translated into the same duplicate outcome the non-racing path
     * returns - never a raw {@link DataIntegrityViolationException} escaping to the caller. Any
     * other integrity violation is deliberately rethrown rather than misclassified as a canonical
     * duplicate.
     */
    public VacancyCreationResult createIfAbsent(VacancyCreationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Vacancy creation command must not be null");
        }
        CanonicalVacancyUrl canonical = VacancyUrlCanonicalizer.canonicalize(URI.create(command.url()));

        Optional<Vacancy> existing = vacancyRepository.findByCanonicalUrl(canonical);
        if (existing.isPresent()) {
            return new VacancyCreationResult(existing.get(), false);
        }

        return attemptCreation(command, canonical);
    }

    private VacancyCreationResult attemptCreation(VacancyCreationCommand command, CanonicalVacancyUrl canonical) {
        try {
            return isolatedCreationTransaction.execute(status -> createInIsolatedTransaction(command, canonical));
        } catch (DataIntegrityViolationException e) {
            if (!isCanonicalUrlConflict(e)) {
                throw e;
            }
            log.debug("Lost a concurrent race for canonical URL {}; returning the winning row", canonical.value());
            return resolveExisting(command, canonical);
        }
    }

    /**
     * Everything here runs inside the single isolated {@code REQUIRES_NEW} transaction: the
     * recheck, resolving/creating the {@link Company} (via {@link CompanyService}, whose own
     * {@code @Transactional(REQUIRED)} simply joins this already-active transaction rather than
     * starting a separate one), building the {@link Vacancy} candidate around that company, and
     * the insert itself. If the insert fails, this entire method's work - including any newly
     * created {@code Company} - rolls back together as one unit; if it commits, both commit
     * together. A {@code Company} is only ever resolved/created after confirming (again) that no
     * vacancy already exists under this canonical URL.
     */
    private VacancyCreationResult createInIsolatedTransaction(VacancyCreationCommand command, CanonicalVacancyUrl canonical) {
        Optional<Vacancy> existing = vacancyRepository.findByCanonicalUrl(canonical);
        if (existing.isPresent()) {
            return new VacancyCreationResult(existing.get(), false);
        }

        Company company = companyService.findOrCreateByName(command.companyName());
        Vacancy candidate = buildVacancy(command, company, canonical);

        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(candidate);
        if (result.isInserted()) {
            // insertVacancyIfAbsent's native RETURNING * maps a fresh entity whose own `company`
            // is an uninitialized lazy proxy - safe only while this transaction's session is
            // still open. Since `company` above is already a fully loaded, real object (not a
            // proxy), assigning it directly here is a plain in-memory field write, not a
            // Hibernate-lazy-load - so the returned Vacancy stays safe to read from after this
            // isolated transaction (and its session) has closed.
            Vacancy inserted = result.vacancy();
            inserted.setCompany(company);
            return new VacancyCreationResult(inserted, true);
        }
        // saveIfAbsent's own ON CONFLICT(url) DO NOTHING silently absorbed a raw-url duplicate -
        // no exception was thrown, so this still commits normally; resolve and report the winner.
        return resolveExisting(command, canonical);
    }

    private Vacancy buildVacancy(VacancyCreationCommand command, Company company, CanonicalVacancyUrl canonical) {
        return Vacancy.builder()
                .company(company)
                .title(command.title())
                .description(command.description())
                .url(command.url())
                .canonicalUrl(canonical.value())
                .location(command.location())
                .remoteMode(command.remoteMode())
                .salaryMin(command.salaryMin())
                .salaryMax(command.salaryMax())
                .currency(command.currency())
                .salaryText(command.salaryText())
                .source(command.source())
                .postedAt(command.postedAt())
                .build();
    }

    /**
     * Called either from inside the still-open isolated transaction (the raw-{@code url} "already
     * exists" case, which never threw) or after it has fully rolled back (the canonical-conflict
     * case) - either way this always runs in a valid transaction, since a rollback only ever
     * affects the isolated transaction itself, never the caller's own (suspended, untouched)
     * transaction.
     */
    private VacancyCreationResult resolveExisting(VacancyCreationCommand command, CanonicalVacancyUrl canonical) {
        return vacancyRepository.findByCanonicalUrl(canonical)
                .or(() -> vacancyRepository.findByUrl(command.url()))
                .map(existing -> new VacancyCreationResult(existing, false))
                .orElseThrow(() -> new IllegalStateException(
                        "Vacancy creation reported a duplicate but no existing row was found for url "
                                + command.url()));
    }

    /**
     * Prefers Hibernate's own structural constraint-name extraction - {@link
     * ConstraintViolationException#getConstraintName()}, which for PostgreSQL is populated by
     * parsing the standard {@code duplicate key value violates unique constraint "name"} error
     * text via Hibernate's own dialect-aware extractor, not a project-specific driver dependency
     * - so a differently-named constraint violation (including {@code vacancy_company_id_fkey},
     * which should never occur in the corrected flow above, but must never be misclassified as a
     * duplicate if it somehow did) is confidently rethrown rather than guessed at. Falls back to
     * matching the constraint name against the deepest {@link java.sql.SQLException}'s message
     * only when no constraint name could be extracted structurally at all; that fallback is
     * exercised by a real-PostgreSQL integration test ({@code VacancyRepositoryTest}) so it is
     * never purely theoretical.
     */
    private boolean isCanonicalUrlConflict(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationException(e);
        if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
            return CANONICAL_URL_UNIQUE_INDEX_NAME.equals(constraintViolation.getConstraintName());
        }
        return matchesConstraintNameInDeepestMessage(e);
    }

    private ConstraintViolationException findConstraintViolationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean matchesConstraintNameInDeepestMessage(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(CANONICAL_URL_UNIQUE_INDEX_NAME);
    }
}
