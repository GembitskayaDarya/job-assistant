package com.darya.jobassistant.vacancies.service;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * The single production entry point for creating a new {@link Vacancy}. Every current and future
 * creation path (manual/guided import, automatic RemoteOK ingestion, and any future discovery
 * source) must go through this method rather than calling {@link VacancyRepository#saveIfAbsent}
 * directly, so canonicalization is computed exactly once and is never duplicated or accidentally
 * skipped by a new caller.
 *
 * <p>No Firecrawl-specific logic lives here - this class only knows about the provider-neutral
 * {@link VacancyUrlCanonicalizer} and the {@link Vacancy} persistence model.
 *
 * <h2>Why the insert attempt runs in its own transaction</h2>
 *
 * PostgreSQL aborts the entire physical transaction a statement error occurred in - catching the
 * resulting {@link DataIntegrityViolationException} in Java does not undo that at the database
 * level. Every other statement attempted on that same connection/transaction (including the
 * winner-resolution lookup below) would fail too, and - worse - so would anything else sharing
 * that transaction with this call (e.g. {@code VacancyImportReviewService}'s own {@code
 * newTransaction} around session completion, or every other offer in {@code
 * VacancyIngestionService.ingest}'s batch loop, since that class carries a class-level {@code
 * @Transactional}). The insert attempt is therefore isolated in its own {@code
 * PROPAGATION_REQUIRES_NEW} transaction (via {@link #isolatedInsertTransaction}, matching the
 * {@code TransactionTemplate}-based isolation pattern already established in {@code
 * VacancyImportService}/{@code VacancyImportReviewService}): only that inner transaction is
 * aborted and rolled back on a conflict, and the caller's own transaction (if any) - suspended,
 * never touched - resumes afterward exactly as valid as before, so the winner lookup and every
 * other statement in the caller's transaction keep working normally.
 */
@Slf4j
@Service
public class VacancyCreationService {

    /** Must match the index name in {@code V12__vacancy_canonical_url.sql}. */
    private static final String CANONICAL_URL_UNIQUE_INDEX_NAME = "uk_vacancy_canonical_url";

    private final VacancyRepository vacancyRepository;
    private final TransactionTemplate isolatedInsertTransaction;

    public VacancyCreationService(VacancyRepository vacancyRepository, PlatformTransactionManager transactionManager) {
        this.vacancyRepository = vacancyRepository;
        this.isolatedInsertTransaction = new TransactionTemplate(transactionManager);
        this.isolatedInsertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * {@code candidate} must already have every field the caller wants persisted set, including
     * {@code url}; {@code canonicalUrl} is computed and overwritten here regardless of whatever
     * the caller may have left there.
     *
     * <p>Sequence: canonicalize -&gt; look up an existing vacancy by that canonical identity -&gt;
     * attempt the insert in an isolated transaction. There is still a race between that lookup
     * and the insert; the database's {@code uk_vacancy_canonical_url} partial unique index is the
     * actual source of truth for novelty, and a violation of it is translated into the same
     * duplicate outcome the non-racing path returns - never a raw {@link
     * DataIntegrityViolationException} escaping to the caller. Any other integrity violation is
     * deliberately rethrown rather than misclassified as a canonical duplicate.
     */
    @Transactional
    public VacancyCreationResult createIfAbsent(Vacancy candidate) {
        String sourceUrl = candidate.getUrl();
        if (!StringUtils.hasText(sourceUrl)) {
            throw new IllegalArgumentException("Vacancy candidate has no URL, cannot persist: " + candidate.getTitle());
        }
        CanonicalVacancyUrl canonical = VacancyUrlCanonicalizer.canonicalize(URI.create(sourceUrl));
        candidate.setCanonicalUrl(canonical.value());

        Optional<Vacancy> existing = vacancyRepository.findByCanonicalUrl(canonical);
        if (existing.isPresent()) {
            return new VacancyCreationResult(existing.get(), false);
        }

        Optional<Vacancy> inserted = attemptInsert(candidate, canonical);
        return inserted
                .map(vacancy -> new VacancyCreationResult(vacancy, true))
                .orElseGet(() -> resolveExisting(candidate, canonical));
    }

    /**
     * Runs the insert in its own {@code REQUIRES_NEW} transaction (see class javadoc) and
     * returns empty for either kind of "someone beat us to it" outcome: a raw-{@code url}
     * conflict, which {@link VacancyRepository#saveIfAbsent}'s {@code ON CONFLICT ... DO NOTHING}
     * resolves without ever throwing, or a genuine {@code uk_vacancy_canonical_url} violation,
     * caught here after the isolated transaction has already rolled back. An insert that
     * returns no row is never reported as created.
     */
    private Optional<Vacancy> attemptInsert(Vacancy candidate, CanonicalVacancyUrl canonical) {
        try {
            return isolatedInsertTransaction.execute(status -> {
                VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(candidate);
                return result.isInserted() ? Optional.of(result.vacancy()) : Optional.<Vacancy>empty();
            });
        } catch (DataIntegrityViolationException e) {
            if (!isCanonicalUrlConflict(e)) {
                throw e;
            }
            log.debug("Lost a concurrent race for canonical URL {}; returning the winning row", canonical.value());
            return Optional.empty();
        }
    }

    /**
     * Called only after the isolated insert transaction above has already committed (the
     * raw-{@code url} "already exists" case) or fully rolled back (the canonical-conflict case),
     * so this always runs in a clean transaction - either the caller's own (never touched by the
     * failed insert, since that ran in a separate, isolated one) or, if none is active, a default
     * one Spring Data JPA opens for the query itself.
     */
    private VacancyCreationResult resolveExisting(Vacancy candidate, CanonicalVacancyUrl canonical) {
        return vacancyRepository.findByCanonicalUrl(canonical)
                .or(() -> vacancyRepository.findByUrl(candidate.getUrl()))
                .map(existing -> new VacancyCreationResult(existing, false))
                .orElseThrow(() -> new IllegalStateException(
                        "Vacancy creation reported a duplicate but no existing row was found for url "
                                + candidate.getUrl()));
    }

    /**
     * Prefers Hibernate's own structural constraint-name extraction - {@link
     * ConstraintViolationException#getConstraintName()}, which for PostgreSQL is populated by
     * parsing the standard {@code duplicate key value violates unique constraint "name"} error
     * text via Hibernate's own dialect-aware extractor, not a project-specific driver dependency
     * - so a differently-named constraint violation is confidently rethrown rather than guessed
     * at. Falls back to matching the constraint name against the deepest {@link
     * java.sql.SQLException}'s message only when no constraint name could be extracted
     * structurally at all; that fallback is exercised by a real-PostgreSQL integration test
     * ({@code VacancyRepositoryTest}) so it is never purely theoretical.
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
