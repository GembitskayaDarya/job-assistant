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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * <h2>Transaction ownership: this class participates, it never owns</h2>
 *
 * {@link #createIfAbsent} is {@code @Transactional(propagation = Propagation.MANDATORY)} - it
 * requires an already-active transaction and joins it; if called without one, Spring fails fast
 * with {@code IllegalTransactionStateException} rather than silently doing nothing transactional.
 * This is deliberate, and is the result of two prior, incomplete designs:
 *
 * <ul>
 *   <li>Originally, a caller resolved/created {@code Company} in its own scope and handed this
 *       method an already-built {@code Vacancy}; the insert ran in an internally-owned {@code
 *       REQUIRES_NEW} transaction that could not see the caller's still-uncommitted {@code
 *       Company} row, producing a real {@code vacancy_company_id_fkey} failure.
 *   <li>The next fix moved {@code Company} resolution in here and gave this class its own {@code
 *       REQUIRES_NEW} {@code TransactionTemplate}, so {@code Company} and {@code Vacancy} always
 *       committed together - but that transaction was independent of whatever the caller was
 *       doing, so for manual import specifically, the {@code Vacancy} could commit *before* the
 *       import session was validated and linked to it. A later, unrelated failure while
 *       completing the session then left a committed, orphaned {@code Vacancy} with no session
 *       ever pointing to it.
 * </ul>
 *
 * <p>The real fix is that transaction ownership belongs to the complete use case, not to this
 * class: {@code VacancyImportReviewService} now owns one transaction that includes session
 * validation, this method's work, and session completion, all as one atomic unit (see its
 * javadoc); {@code VacancyIngestionService} owns one transaction per {@code JobOffer}. This class
 * simply joins whichever transaction its caller is already running.
 *
 * <h2>Canonical conflicts are not resolved here</h2>
 *
 * Because this method no longer owns its transaction, it cannot safely look up the winning row
 * itself when it loses a race on {@code uk_vacancy_canonical_url}: PostgreSQL has already aborted
 * the physical transaction at that point, and any further statement on it - including a lookup -
 * would fail too, and so would every other statement the caller's transaction contains. Instead,
 * {@link CanonicalVacancyCreationConflictException} is thrown and left to propagate all the way
 * out of the caller's transaction boundary, rolling back everything that transaction did
 * (including any {@code Company} this method created). The caller is responsible for retrying the
 * whole use case, once, in a fresh transaction - see {@code VacancyImportReviewService} and {@code
 * VacancyIngestionService} for how each does that.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyCreationService {

    /** Must match the index name in {@code V12__vacancy_canonical_url.sql}. */
    private static final String CANONICAL_URL_UNIQUE_INDEX_NAME = "uk_vacancy_canonical_url";

    private final VacancyRepository vacancyRepository;
    private final CompanyService companyService;

    /**
     * Requires an already-active transaction (see class javadoc) and does everything within it:
     * look up an existing vacancy by canonical URL; if absent, resolve/create the {@link Company}
     * and insert. Never commits or rolls back anything itself, never suspends the caller's
     * transaction, and never calls AI, Telegram, or scheduler code.
     *
     * @throws CanonicalVacancyCreationConflictException if the insert lost a race on {@code
     *     uk_vacancy_canonical_url}; the caller's whole transaction must be allowed to roll back
     *     and the use case retried in a fresh one
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public VacancyCreationResult createIfAbsent(VacancyCreationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Vacancy creation command must not be null");
        }
        CanonicalVacancyUrl canonical = VacancyUrlCanonicalizer.canonicalize(URI.create(command.url()));

        Optional<Vacancy> existing = vacancyRepository.findByCanonicalUrl(canonical);
        if (existing.isPresent()) {
            return new VacancyCreationResult(existing.get(), false);
        }

        Company company = companyService.findOrCreateByName(command.companyName());
        Vacancy candidate = buildVacancy(command, company, canonical);

        VacancyPersistenceResult result;
        try {
            result = vacancyRepository.saveIfAbsent(candidate);
        } catch (DataIntegrityViolationException e) {
            if (!isCanonicalUrlConflict(e)) {
                throw e;
            }
            throw new CanonicalVacancyCreationConflictException(
                    "Lost a concurrent race for canonical URL " + canonical.value(), e);
        }

        if (result.isInserted()) {
            // insertVacancyIfAbsent's native RETURNING * maps a fresh entity whose own `company`
            // is an uninitialized lazy proxy - safe only while this transaction's session is
            // still open. Since `company` above is already a fully loaded, real object (not a
            // proxy), assigning it directly here is a plain in-memory field write, not a
            // Hibernate-lazy-load - so the returned Vacancy stays safe to read from even after
            // this transaction eventually closes.
            Vacancy inserted = result.vacancy();
            inserted.setCompany(company);
            return new VacancyCreationResult(inserted, true);
        }
        // saveIfAbsent's own ON CONFLICT(url) DO NOTHING silently absorbed a raw-url duplicate -
        // no exception was thrown, so the transaction is still healthy; resolve and report the
        // winner by canonical URL first, then by raw URL for legacy (pre-canonicalization) rows.
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
