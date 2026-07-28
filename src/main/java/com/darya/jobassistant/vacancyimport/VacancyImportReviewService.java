package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.ai.AnalyzeVacancyUseCase;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.CanonicalVacancyCreationConflictException;
import com.darya.jobassistant.vacancies.service.VacancyCreationService;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
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
 * different collaborator ({@link VacancyCreationService}) that the extraction pipeline has no
 * reason to depend on.
 *
 * <p>Follows the same no-ambient-{@code @Transactional} discipline as {@link VacancyImportService}
 * and for the same reason: every write goes through its own {@link TransactionTemplate} call, so
 * each one commits (or rolls back) independently rather than sharing one transaction that could
 * hold locks across unrelated operations.
 *
 * <h2>Save is one atomic unit of work, owned entirely by this class</h2>
 *
 * {@link VacancyCreationService#createIfAbsent} is {@code Propagation.MANDATORY}: it never starts
 * or commits a transaction of its own, it only joins whichever one is already active. For Save,
 * that active transaction is {@link #attemptSave}'s single {@code newTransaction.execute(...)}
 * call, which covers reloading and revalidating the session, creating the {@code Vacancy} (and
 * any {@code Company} it needed), linking it to the session, and conditionally completing the
 * session - all as one physical commit or rollback. This replaced an earlier design where {@code
 * VacancyCreationService} owned its own separate {@code REQUIRES_NEW} transaction: a {@code
 * Vacancy} could commit there before this class's own session-completion transaction ran, so if
 * completion later failed for any reason, the already-committed {@code Vacancy} was orphaned -
 * created, but never linked to any session. Under the current design a failure anywhere in {@link
 * #attemptSave} - including a losing conditional session completion, see {@code
 * status.setRollbackOnly()} below - rolls back the {@code Vacancy}/{@code Company} right along
 * with the session update, so no orphan can be left behind.
 *
 * <p>A canonical-URL race ({@link CanonicalVacancyCreationConflictException}) is caught only in
 * {@link #performSave}, strictly outside the failed {@code newTransaction.execute(...)} call - by
 * the time it's caught, that transaction has already rolled back completely. {@link #performSave}
 * then retries the whole use case once, in a brand-new transaction via a second {@link
 * #attemptSave} call: by then the competing insert has committed, so the retry's {@code
 * createIfAbsent} simply finds the winning row and reports {@code newlyCreated() == false}. A
 * second conflict on the retry is not retried again. {@link #resolveLostRace} still exists for the
 * distinct case of losing the session-completion race itself (a concurrent Retry/Cancel/expiry, or
 * another concurrent Save, won first) - it reports whichever operation actually won, rather than a
 * generic failure.
 */
@Slf4j
@Service
public class VacancyImportReviewService implements ReviewVacancyImportUseCase, AnalyzeImportedVacancyUseCase {

    private static final String FALLBACK_SOURCE = "manual_telegram";

    private final VacancyImportSessionRepository sessionRepository;
    private final VacancyImportDraftRepository draftRepository;
    private final VacancyRepository vacancyRepository;
    private final VacancyCreationService vacancyCreationService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final AnalyzeVacancyUseCase analyzeVacancyUseCase;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public VacancyImportReviewService(
            VacancyImportSessionRepository sessionRepository,
            VacancyImportDraftRepository draftRepository,
            VacancyRepository vacancyRepository,
            VacancyCreationService vacancyCreationService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            AnalyzeVacancyUseCase analyzeVacancyUseCase,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.draftRepository = draftRepository;
        this.vacancyRepository = vacancyRepository;
        this.vacancyCreationService = vacancyCreationService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.analyzeVacancyUseCase = analyzeVacancyUseCase;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public AnalyzeImportedVacancyResult analyze(UUID importSessionId, long telegramChatId, long telegramUserId) {
        Optional<VacancyImportSession> maybeSession = sessionRepository.findSessionById(importSessionId);
        if (maybeSession.isEmpty()) {
            return new AnalyzeImportedVacancyResult.NotAvailable();
        }
        VacancyImportSession session = maybeSession.get();
        if (session.getTelegramChatId() != telegramChatId || session.getTelegramUserId() != telegramUserId) {
            // Deliberately identical to the "not found" branch above - see review()'s equivalent
            // ownership check for why.
            return new AnalyzeImportedVacancyResult.NotAvailable();
        }
        if (session.getState() != ImportState.COMPLETED) {
            return new AnalyzeImportedVacancyResult.InvalidState(session.getState());
        }
        UUID vacancyId = session.getVacancyId();
        if (vacancyId == null) {
            log.error("Invariant violation: session {} is COMPLETED but has no linked vacancy", importSessionId);
            return new AnalyzeImportedVacancyResult.MissingLinkedVacancy(importSessionId);
        }

        AnalyzeVacancyResult result = analyzeVacancyUseCase.analyze(vacancyId);
        return switch (result) {
            case AnalyzeVacancyResult.Available(var vacancy, var analysis, var newlyCreated) ->
                    new AnalyzeImportedVacancyResult.Available(importSessionId, vacancyId, vacancy, analysis, newlyCreated);
            case AnalyzeVacancyResult.InProgress ignored -> new AnalyzeImportedVacancyResult.InProgress();
            case AnalyzeVacancyResult.Failed ignored -> new AnalyzeImportedVacancyResult.Failed();
            // The linked vacancy was deleted after Save completed - not expected in current
            // behavior (nothing deletes a Vacancy), but handled as the same safe invariant failure
            // as a missing link rather than silently falling through.
            case AnalyzeVacancyResult.VacancyNotFound ignored -> {
                log.error("Invariant violation: session {} links to vacancy {} which no longer exists", importSessionId, vacancyId);
                yield new AnalyzeImportedVacancyResult.MissingLinkedVacancy(importSessionId);
            }
        };
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
            case WAITING_FOR_CONFIRMATION -> performSave(session.getId());
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

    /**
     * Runs {@link #attemptSave} once; if it loses a canonical-URL race, retries the whole use case
     * exactly once more in a fresh transaction (see class javadoc). Both attempts, and any other
     * failure, are caught here - strictly outside {@link #attemptSave}'s own transaction boundary -
     * so a caught exception never masks whether that transaction actually rolled back.
     */
    private ReviewVacancyImportResult performSave(UUID sessionId) {
        SaveAttempt attempt;
        try {
            attempt = attemptSave(sessionId);
        } catch (CanonicalVacancyCreationConflictException firstConflict) {
            log.debug("Lost a canonical URL race while saving session {}; retrying once", sessionId, firstConflict);
            try {
                attempt = attemptSave(sessionId);
            } catch (RuntimeException retryFailure) {
                log.warn("Vacancy save failed for session {} on retry", sessionId, retryFailure);
                return new ReviewVacancyImportResult.Failed();
            }
        } catch (RuntimeException e) {
            log.warn("Vacancy save failed for session {}", sessionId, e);
            return new ReviewVacancyImportResult.Failed();
        }

        return switch (attempt) {
            case SaveAttempt.Saved(var jobOffer, var newlyCreated) ->
                    new ReviewVacancyImportResult.Saved(sessionId, jobOffer, newlyCreated);
            case SaveAttempt.DraftMissing ignored -> new ReviewVacancyImportResult.DraftMissing(sessionId);
            case SaveAttempt.NotWaitingForConfirmation ignored -> resolveLostRace(sessionId);
            case SaveAttempt.LostRace ignored -> resolveLostRace(sessionId);
        };
    }

    /**
     * The complete Save use case as one atomic unit: reload the session by id (not the possibly
     * stale in-memory instance {@link #review} first loaded - a concurrent operation may have
     * changed it since), confirm it is still {@code WAITING_FOR_CONFIRMATION}, reload the draft,
     * create/find the {@code Vacancy} via {@link VacancyCreationService#createIfAbsent} (which
     * joins this transaction), link it to the session, and conditionally complete the session. A
     * {@link CanonicalVacancyCreationConflictException} thrown by {@code createIfAbsent} is
     * deliberately left to propagate out of {@code newTransaction.execute(...)} uncaught, so the
     * whole transaction - including the {@code Company}/{@code Vacancy} insert attempt - rolls
     * back; see {@link #performSave} for where it's caught and retried.
     */
    private SaveAttempt attemptSave(UUID sessionId) {
        return newTransaction.execute(status -> {
            Optional<VacancyImportSession> maybeSession = sessionRepository.findSessionById(sessionId);
            if (maybeSession.isEmpty() || maybeSession.get().getState() != ImportState.WAITING_FOR_CONFIRMATION) {
                status.setRollbackOnly();
                return new SaveAttempt.NotWaitingForConfirmation();
            }
            VacancyImportSession session = maybeSession.get();

            Optional<VacancyImportDraft> maybeDraft = draftRepository.findDraftBySessionId(sessionId);
            if (maybeDraft.isEmpty()) {
                status.setRollbackOnly();
                return new SaveAttempt.DraftMissing();
            }
            VacancyImportDraft draft = maybeDraft.get();

            VacancyCreationResult creationResult = vacancyCreationService.createIfAbsent(buildCreationCommand(session, draft));
            Vacancy vacancy = creationResult.vacancy();
            session.complete(vacancy.getId(), clock);
            boolean transitioned = sessionRepository.completeIfWaitingForConfirmation(sessionId, vacancy.getId(), session.getUpdatedAt());
            if (!transitioned) {
                status.setRollbackOnly();
                return new SaveAttempt.LostRace();
            }
            // Mapped while the transaction (and its persistence context) is still open, so the
            // vacancy's lazily-fetched company resolves here rather than after commit.
            JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
            return new SaveAttempt.Saved(jobOffer, creationResult.newlyCreated());
        });
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
     * Builds the creation command from the session's own preserved inputs, not the preview text:
     * {@code description} is the untouched raw description the user sent (never the AI
     * extraction, never a summary), and {@code url} is the normalized source URL captured back in
     * step 2 of the import. {@code location}, {@code remoteMode} and {@code salaryText} are
     * copied verbatim from the draft; {@code contractTypes} and {@code requiredSkills} still have
     * no corresponding column and are left out rather than growing the schema for this step.
     *
     * <p>Deliberately does not resolve a {@code Company} here (unlike before this class's
     * transaction-ownership correction): a {@code Company} the caller resolved outside {@link
     * VacancyCreationService}'s own isolated transaction could end up existing only in this
     * method's suspended, uncommitted transaction while the {@code Vacancy} insert runs in a
     * separate {@code REQUIRES_NEW} one - exactly the {@code vacancy_company_id_fkey} failure
     * that correction fixed.
     */
    private VacancyCreationCommand buildCreationCommand(VacancyImportSession session, VacancyImportDraft draft) {
        return new VacancyCreationCommand(
                draft.data().company(),
                draft.data().title(),
                session.getRawDescription(),
                session.getSourceUrl(),
                draft.data().location(),
                draft.data().remotePolicy(),
                null,
                null,
                null,
                draft.data().salaryText(),
                sourceIdentifier(session.getSourceUrl()),
                null);
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

    /**
     * The result of one {@link #attemptSave} transaction attempt. Kept private and sealed so
     * {@link #performSave} is forced to handle every outcome explicitly, including ones that can
     * only arise from a race lost to a concurrent operation.
     */
    private sealed interface SaveAttempt {
        record Saved(JobOffer jobOffer, boolean newlyCreated) implements SaveAttempt {
        }

        record DraftMissing() implements SaveAttempt {
        }

        record NotWaitingForConfirmation() implements SaveAttempt {
        }

        record LostRace() implements SaveAttempt {
        }
    }
}
