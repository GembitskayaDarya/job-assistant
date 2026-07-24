package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportDraftRepository;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies the confirmation-step decision (Save/Retry/Cancel) to a {@code WAITING_FOR_CONFIRMATION}
 * session. Kept separate from {@link VacancyImportService}: that class owns the URL/description/
 * extraction pipeline, this one owns the review-and-finalize pipeline, and Save pulls in a
 * different set of collaborators ({@link VacancyRepository}, {@link CompanyService}) that the
 * extraction pipeline has no reason to depend on.
 *
 * <p>Follows the same no-ambient-{@code @Transactional} discipline as {@link VacancyImportService}
 * and for the same reason: every write goes through its own {@link TransactionTemplate} call, so
 * each one commits (or rolls back) independently rather than sharing one transaction that could
 * hold locks across unrelated operations. Unlike extraction, Save has no external provider call to
 * keep outside a transaction - creating/finding the {@code Vacancy} and completing the session
 * happen together in one transaction, exactly per the conceptual Save transaction in the project's
 * design notes, so a losing conditional completion can roll back an orphan Vacancy insert too.
 */
@Slf4j
@Service
public class VacancyImportReviewService implements ReviewVacancyImportUseCase {

    private static final String FALLBACK_SOURCE = "manual_telegram";

    private final VacancyImportSessionRepository sessionRepository;
    private final VacancyImportDraftRepository draftRepository;
    private final VacancyRepository vacancyRepository;
    private final CompanyService companyService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public VacancyImportReviewService(
            VacancyImportSessionRepository sessionRepository,
            VacancyImportDraftRepository draftRepository,
            VacancyRepository vacancyRepository,
            CompanyService companyService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.draftRepository = draftRepository;
        this.vacancyRepository = vacancyRepository;
        this.companyService = companyService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public ReviewVacancyImportResult review(UUID sessionId, long telegramChatId, long telegramUserId, VacancyImportAction action) {
        Optional<VacancyImportSession> maybeSession = sessionRepository.findSessionById(sessionId);
        if (maybeSession.isEmpty()) {
            return new ReviewVacancyImportResult.NotAvailable();
        }
        VacancyImportSession session = maybeSession.get();
        if (session.getTelegramChatId() != telegramChatId || session.getTelegramUserId() != telegramUserId) {
            // Deliberately identical to the "not found" branch above: confirming or denying that
            // a session with this id exists for someone else would leak information to a caller
            // who has only proven they can guess/replay a UUID, not that they own it.
            return new ReviewVacancyImportResult.NotAvailable();
        }

        if (session.getState() == ImportState.WAITING_FOR_CONFIRMATION && isExpired(session)) {
            return expireSession(session);
        }

        return switch (action) {
            case SAVE -> handleSave(session);
            case RETRY -> handleRetry(session);
            case CANCEL -> handleCancel(session);
        };
    }

    private ReviewVacancyImportResult handleSave(VacancyImportSession session) {
        return switch (session.getState()) {
            case WAITING_FOR_CONFIRMATION -> performSave(session);
            case COMPLETED -> loadCompletedResult(session);
            default -> new ReviewVacancyImportResult.InvalidState(session.getId(), session.getState());
        };
    }

    private ReviewVacancyImportResult handleRetry(VacancyImportSession session) {
        return switch (session.getState()) {
            case WAITING_FOR_CONFIRMATION -> performRetry(session);
            default -> new ReviewVacancyImportResult.InvalidState(session.getId(), session.getState());
        };
    }

    private ReviewVacancyImportResult handleCancel(VacancyImportSession session) {
        return switch (session.getState()) {
            case WAITING_FOR_CONFIRMATION -> performCancel(session);
            // Repeated Cancel is safe/idempotent: report the same outcome without re-touching the row.
            case CANCELLED -> new ReviewVacancyImportResult.Cancelled(session.getId());
            default -> new ReviewVacancyImportResult.InvalidState(session.getId(), session.getState());
        };
    }

    private ReviewVacancyImportResult performSave(VacancyImportSession session) {
        UUID sessionId = session.getId();
        Optional<VacancyImportDraft> maybeDraft = draftRepository.findDraftBySessionId(sessionId);
        if (maybeDraft.isEmpty()) {
            return new ReviewVacancyImportResult.DraftMissing(sessionId);
        }
        VacancyImportDraft draft = maybeDraft.get();

        try {
            SaveOutcome outcome = newTransaction.execute(status -> {
                VacancyOutcome vacancyOutcome = saveOrFindVacancy(buildVacancyCandidate(session, draft));
                Vacancy vacancy = vacancyOutcome.vacancy();
                session.complete(vacancy.getId(), clock);
                boolean transitioned = sessionRepository.completeIfWaitingForConfirmation(sessionId, vacancy.getId(), session.getUpdatedAt());
                if (!transitioned) {
                    status.setRollbackOnly();
                }
                // Mapped while the transaction (and its persistence context) is still open, so the
                // vacancy's lazily-fetched company resolves here rather than after commit.
                JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
                return new SaveOutcome(jobOffer, transitioned, vacancyOutcome.newlyCreated());
            });
            if (outcome.transitioned()) {
                return new ReviewVacancyImportResult.Saved(sessionId, outcome.vacancy(), outcome.newlyCreated());
            }
            return resolveLostRace(sessionId);
        } catch (RuntimeException e) {
            log.warn("Vacancy save failed for session {}", sessionId, e);
            return new ReviewVacancyImportResult.Failed();
        }
    }

    private ReviewVacancyImportResult performRetry(VacancyImportSession session) {
        UUID sessionId = session.getId();
        session.retryDescription(clock);
        Instant updatedAt = session.getUpdatedAt();
        boolean transitioned = newTransaction.execute(status -> {
            draftRepository.deleteBySessionId(sessionId);
            boolean applied = sessionRepository.retryIfWaitingForConfirmation(sessionId, updatedAt);
            if (!applied) {
                status.setRollbackOnly();
            }
            return applied;
        });
        if (transitioned) {
            return new ReviewVacancyImportResult.RetryRequested(sessionId);
        }
        return resolveLostRace(sessionId);
    }

    private ReviewVacancyImportResult performCancel(VacancyImportSession session) {
        UUID sessionId = session.getId();
        session.cancel(clock);
        boolean transitioned = newTransaction.execute(status ->
                sessionRepository.cancelIfWaitingForConfirmation(sessionId, session.getUpdatedAt()));
        if (transitioned) {
            return new ReviewVacancyImportResult.Cancelled(sessionId);
        }
        return resolveLostRace(sessionId);
    }

    private ReviewVacancyImportResult expireSession(VacancyImportSession session) {
        UUID sessionId = session.getId();
        session.expire(clock);
        boolean applied = newTransaction.execute(status ->
                sessionRepository.expireIfWaitingForConfirmation(sessionId, session.getUpdatedAt()));
        if (applied) {
            return new ReviewVacancyImportResult.Expired(sessionId);
        }
        return resolveLostRace(sessionId);
    }

    private boolean isExpired(VacancyImportSession session) {
        return !Instant.now(clock).isBefore(session.getExpiresAt());
    }

    /**
     * Reloads a session after this call's own conditional update applied to zero rows (another
     * operation - a concurrent Save, Retry, Cancel, or expiry - won first) and reports whatever
     * that operation actually landed on, rather than blindly reporting this call's own intended
     * outcome or a generic failure.
     */
    private ReviewVacancyImportResult resolveLostRace(UUID sessionId) {
        Optional<VacancyImportSession> current = sessionRepository.findSessionById(sessionId);
        if (current.isEmpty()) {
            return new ReviewVacancyImportResult.NotAvailable();
        }
        VacancyImportSession session = current.get();
        return switch (session.getState()) {
            case COMPLETED -> loadCompletedResult(session);
            case CANCELLED -> new ReviewVacancyImportResult.Cancelled(sessionId);
            case EXPIRED -> new ReviewVacancyImportResult.Expired(sessionId);
            default -> new ReviewVacancyImportResult.Failed();
        };
    }

    private ReviewVacancyImportResult loadCompletedResult(VacancyImportSession session) {
        UUID sessionId = session.getId();
        UUID vacancyId = session.getVacancyId();
        if (vacancyId == null) {
            log.error("Invariant violation: session {} is COMPLETED but has no linked vacancy", sessionId);
            return new ReviewVacancyImportResult.Failed();
        }
        return vacancyRepository.findByIdWithCompany(vacancyId)
                .<ReviewVacancyImportResult>map(vacancy ->
                        new ReviewVacancyImportResult.Saved(sessionId, vacancyJobOfferMapper.toJobOffer(vacancy), false))
                .orElseGet(() -> {
                    log.error("Invariant violation: session {} links to vacancy {} which no longer exists", sessionId, vacancyId);
                    return new ReviewVacancyImportResult.Failed();
                });
    }

    /**
     * Reuses {@link VacancyRepository#saveIfAbsent} - the same URL-based, database-enforced
     * dedup primitive automatic ingestion already relies on - rather than a find-then-insert check
     * from application code, so a manual import can never race its way into a duplicate Vacancy.
     * An existing vacancy for the same URL is reused exactly as-is (no field is overwritten):
     * matches {@code VacancyIngestionService}'s established no-merge convention for duplicates.
     */
    private VacancyOutcome saveOrFindVacancy(Vacancy candidate) {
        VacancyPersistenceResult result = vacancyRepository.saveIfAbsent(candidate);
        if (result.isInserted()) {
            return new VacancyOutcome(result.vacancy(), true);
        }
        Vacancy existing = vacancyRepository.findByUrl(candidate.getUrl())
                .orElseThrow(() -> new IllegalStateException(
                        "saveIfAbsent reported an existing vacancy for url " + candidate.getUrl() + " but none was found"));
        return new VacancyOutcome(existing, false);
    }

    /**
     * Builds the candidate {@code Vacancy} from the session's own preserved inputs, not the
     * preview text: {@code description} is the untouched raw description the user sent (never the
     * AI extraction, never a summary), and {@code url} is the normalized source URL captured back
     * in step 2 of the import. Only the structured fields the existing {@code Vacancy} model
     * already supports are taken from the draft (title, company); {@code location},
     * {@code remotePolicy}, {@code contractTypes}, {@code requiredSkills} and {@code salaryText}
     * have no corresponding column and are deliberately left out rather than growing the schema
     * for this step.
     */
    private Vacancy buildVacancyCandidate(VacancyImportSession session, VacancyImportDraft draft) {
        Company company = companyService.findOrCreateByName(draft.data().company());
        return Vacancy.builder()
                .company(company)
                .title(draft.data().title())
                .description(session.getRawDescription())
                .url(session.getSourceUrl())
                .source(sourceIdentifier(session.getSourceUrl()))
                .build();
    }

    /**
     * A source label derived from the URL's hostname (e.g. {@code linkedin.com}), not a
     * hard-coded job-board whitelist - any site works, and the label is purely informational
     * (it is never treated as proof that the data is authentic or accurate). Falls back to a
     * provider-neutral constant if the URL is somehow unparseable, which should not happen since
     * {@code sourceUrl} was already validated as an absolute http/https URL when it was accepted.
     */
    private String sourceIdentifier(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return FALLBACK_SOURCE;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
        } catch (RuntimeException e) {
            return FALLBACK_SOURCE;
        }
    }

    private record VacancyOutcome(Vacancy vacancy, boolean newlyCreated) {
    }

    private record SaveOutcome(JobOffer vacancy, boolean transitioned, boolean newlyCreated) {
    }
}
