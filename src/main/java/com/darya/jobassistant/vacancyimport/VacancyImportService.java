package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import com.darya.jobassistant.vacancyimport.config.VacancyImportProperties;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ExtractVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.exception.VacancyImportSessionException;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportDraftRepository;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import com.darya.jobassistant.vacancyimport.url.VacancyUrlValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deliberately has no class- or method-level {@code @Transactional}: {@link #start} can involve
 * two independent writes (expiring a stale session, then inserting a new one), and each must
 * commit on its own before the next begins. If both instead shared one ambient transaction and
 * the insert used {@code PROPAGATION_REQUIRES_NEW} to isolate its failure (the way {@code
 * UserService} isolates its own race-recovery insert), the outer transaction's uncommitted
 * expire-update would still hold a lock on that row while the suspended-and-resumed inner
 * transaction tried to insert against the same partial unique index - the two can wait on each
 * other indefinitely. Running every write through its own {@link TransactionTemplate} call
 * instead means each one is its own independent, immediately-committed unit, so there is never
 * an outer transaction left open to block on.
 *
 * <p>{@link #provideUrl} and {@link #handleText} share their session lookup/expiry and URL
 * acceptance logic via the private {@link ActiveSessionLookup}/{@link UrlAcceptanceOutcome}
 * helpers below, rather than one calling the other through its injected interface: a same-bean
 * call through {@code this} would skip the Spring proxy entirely (self-invocation never re-enters
 * through the proxy), so routing {@link #handleText} through {@code provideUrl(...)} would gain
 * nothing over calling a plain private method - it would just add an unnecessary extra result
 * type to translate between.
 *
 * <p>{@link #extract} follows the same no-ambient-transaction discipline for the AI extraction
 * call: {@link VacancyExtractionService#extract} - the shared extraction capability also usable by
 * a future automatic-discovery caller, see {@code vacancyextraction} package - runs between two
 * independent {@link TransactionTemplate} calls (persist-the-EXTRACTING-state was already
 * committed by {@link #acceptDescription}; the draft insert and {@code EXTRACTING ->
 * WAITING_FOR_CONFIRMATION} transition happen together afterwards), never inside one. Because this
 * class has no class-level {@code @Transactional} to begin with, {@link #acceptDescription}
 * calling {@link #extract} through a plain {@code this} reference is safe - there is no Spring
 * transaction proxy behavior being bypassed, unlike the self-invocation hazard {@code
 * @Transactional} methods have.
 */
@Slf4j
@Service
public class VacancyImportService
        implements StartVacancyImportUseCase,
                CancelVacancyImportUseCase,
                ProvideVacancyUrlUseCase,
                ContinueVacancyImportUseCase,
                ExtractVacancyImportUseCase {

    private final VacancyImportSessionRepository repository;
    private final VacancyImportDraftRepository draftRepository;
    private final VacancyExtractionService extractionService;
    private final Clock clock;
    private final VacancyImportProperties properties;
    private final TransactionTemplate newTransaction;

    public VacancyImportService(
            VacancyImportSessionRepository repository,
            VacancyImportDraftRepository draftRepository,
            VacancyExtractionService extractionService,
            Clock clock,
            VacancyImportProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.draftRepository = draftRepository;
        this.extractionService = extractionService;
        this.clock = clock;
        this.properties = properties;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public StartVacancyImportResult start(long telegramChatId, long telegramUserId) {
        Optional<VacancyImportSession> active = repository.findActiveSession(telegramChatId, telegramUserId);
        if (active.isPresent()) {
            VacancyImportSession session = active.get();
            if (Instant.now(clock).isBefore(session.getExpiresAt())) {
                return new StartVacancyImportResult.AlreadyActive(session.getId(), session.getState());
            }
            expireAndPersist(session);
        }
        return startNewSession(telegramChatId, telegramUserId);
    }

    @Override
    public CancelVacancyImportResult cancel(long telegramChatId, long telegramUserId) {
        Optional<VacancyImportSession> active = repository.findActiveSession(telegramChatId, telegramUserId);
        if (active.isEmpty()) {
            return CancelVacancyImportResult.NO_ACTIVE_SESSION;
        }
        VacancyImportSession session = active.get();
        session.cancel(clock);
        newTransaction.executeWithoutResult(status -> repository.saveSession(session));
        return CancelVacancyImportResult.CANCELLED;
    }

    @Override
    public ProvideVacancyUrlResult provideUrl(long telegramChatId, long telegramUserId, String rawUrl) {
        ActiveSessionLookup lookup = loadNonExpiredActiveSession(telegramChatId, telegramUserId);
        if (lookup instanceof ActiveSessionLookup.NotFound) {
            return new ProvideVacancyUrlResult.NoActiveSession();
        }
        if (lookup instanceof ActiveSessionLookup.Expired) {
            return new ProvideVacancyUrlResult.SessionExpired();
        }
        VacancyImportSession session = ((ActiveSessionLookup.Found) lookup).session();
        if (session.getState() != ImportState.WAITING_FOR_URL) {
            return new ProvideVacancyUrlResult.UnexpectedState(session.getState());
        }
        return switch (acceptUrl(session, rawUrl)) {
            case UrlAcceptanceOutcome.Accepted(var uri) -> new ProvideVacancyUrlResult.Accepted(session.getId(), uri);
            case UrlAcceptanceOutcome.Invalid(var reason) -> new ProvideVacancyUrlResult.InvalidUrl(reason);
        };
    }

    @Override
    public ContinueVacancyImportResult handleText(long telegramChatId, long telegramUserId, String text) {
        ActiveSessionLookup lookup = loadNonExpiredActiveSession(telegramChatId, telegramUserId);
        if (lookup instanceof ActiveSessionLookup.NotFound) {
            return new ContinueVacancyImportResult.NoActiveSession();
        }
        if (lookup instanceof ActiveSessionLookup.Expired) {
            return new ContinueVacancyImportResult.SessionExpired();
        }
        VacancyImportSession session = ((ActiveSessionLookup.Found) lookup).session();

        return switch (session.getState()) {
            case WAITING_FOR_URL -> switch (acceptUrl(session, text)) {
                case UrlAcceptanceOutcome.Accepted(var uri) ->
                        new ContinueVacancyImportResult.UrlAccepted(session.getId(), uri);
                case UrlAcceptanceOutcome.Invalid(var reason) -> new ContinueVacancyImportResult.InvalidUrl(reason);
            };
            case WAITING_FOR_DESCRIPTION -> acceptDescription(session, text);
            case EXTRACTING, WAITING_FOR_CONFIRMATION -> new ContinueVacancyImportResult.UnexpectedState(session.getState());
            case COMPLETED, CANCELLED, FAILED, EXPIRED -> throw new IllegalStateException(
                    "Active session lookup must never return a terminal state, but was " + session.getState());
        };
    }

    /**
     * Loads the active session for a chat+user, transparently expiring and persisting it first
     * if its TTL has already passed. Shared by every operation that starts with "find my active
     * session" ({@link #provideUrl}, {@link #handleText}) so the expiry check and the write that
     * follows it exist in exactly one place.
     */
    private ActiveSessionLookup loadNonExpiredActiveSession(long telegramChatId, long telegramUserId) {
        Optional<VacancyImportSession> active = repository.findActiveSession(telegramChatId, telegramUserId);
        if (active.isEmpty()) {
            return new ActiveSessionLookup.NotFound();
        }
        VacancyImportSession session = active.get();
        if (!Instant.now(clock).isBefore(session.getExpiresAt())) {
            expireAndPersist(session);
            return new ActiveSessionLookup.Expired();
        }
        return new ActiveSessionLookup.Found(session);
    }

    /**
     * Validates, normalizes, applies and persists a URL against a session already confirmed to
     * be in {@code WAITING_FOR_URL}. Shared by {@link #provideUrl} and {@link #handleText} so
     * URL handling behaves identically regardless of which use case a caller enters through.
     */
    private UrlAcceptanceOutcome acceptUrl(VacancyImportSession session, String rawUrl) {
        VacancyUrlValidator.Result validation = VacancyUrlValidator.validate(rawUrl);
        if (validation instanceof VacancyUrlValidator.Result.Invalid invalid) {
            return new UrlAcceptanceOutcome.Invalid(invalid.reason());
        }
        URI normalizedUrl = ((VacancyUrlValidator.Result.Valid) validation).normalizedUrl();
        session.provideUrl(normalizedUrl.toString(), clock);
        newTransaction.executeWithoutResult(status -> repository.saveSession(session));
        return new UrlAcceptanceOutcome.Accepted(normalizedUrl);
    }

    /**
     * Applies and persists a raw description against a session already confirmed to be in
     * {@code WAITING_FOR_DESCRIPTION}. Trimming is the only normalization done here - the
     * meaningful-content rule itself lives solely in {@link VacancyImportSession#provideDescriptionAndStartExtraction},
     * so there is exactly one minimum-length rule in the whole system.
     *
     * <p>The write itself goes through {@link VacancyImportSessionRepository#acceptDescriptionIfWaiting}
     * rather than a plain {@code saveSession}: two concurrent submissions for the same session
     * would each independently pass domain validation against their own in-memory copy (each
     * copy legitimately starts in {@code WAITING_FOR_DESCRIPTION}), so an unconditional update
     * would let both silently "succeed" and the second write would clobber the first. Guarding
     * the update with the session's expected prior state means only one of the two can actually
     * apply; the other is reported back as a lost race instead of corrupting the row.
     *
     * <p>On success, extraction runs synchronously as part of the same call (see {@link
     * #extract}) so the caller gets back a recognized-or-failed outcome directly, without a
     * separate round trip.
     */
    private ContinueVacancyImportResult acceptDescription(VacancyImportSession session, String rawText) {
        String trimmed = rawText == null ? null : rawText.trim();
        try {
            session.provideDescriptionAndStartExtraction(trimmed, clock);
        } catch (VacancyImportSessionException invalidDescription) {
            return new ContinueVacancyImportResult.InvalidDescription(invalidDescription.getMessage());
        }
        boolean applied = newTransaction.execute(status ->
                repository.acceptDescriptionIfWaiting(session.getId(), session.getRawDescription(), session.getUpdatedAt()));
        if (!applied) {
            // Lost the race: someone else already moved this session on (typically to EXTRACTING)
            // or cancelled/expired it out from under us. Report the still-active state if there
            // is one; if the row is terminal (or gone) by now, "nothing to continue" is the more
            // accurate outcome than inventing a state-carrying result for it.
            return repository.findSessionById(session.getId())
                    .filter(current -> current.getState().isActive())
                    .<ContinueVacancyImportResult>map(current -> new ContinueVacancyImportResult.UnexpectedState(current.getState()))
                    .orElseGet(ContinueVacancyImportResult.NoActiveSession::new);
        }
        return toContinueResult(extract(session.getId()));
    }

    private ContinueVacancyImportResult toContinueResult(ExtractVacancyImportResult result) {
        return switch (result) {
            case ExtractVacancyImportResult.Recognized(var sessionId, var draft) ->
                    new ContinueVacancyImportResult.VacancyRecognized(sessionId, draft);
            case ExtractVacancyImportResult.AlreadyRecognized(var sessionId, var draft) ->
                    new ContinueVacancyImportResult.VacancyRecognized(sessionId, draft);
            case ExtractVacancyImportResult.Failed(var sessionId) -> new ContinueVacancyImportResult.ExtractionFailed(sessionId);
            case ExtractVacancyImportResult.InvalidState(var ignored, var state) ->
                    new ContinueVacancyImportResult.UnexpectedState(state);
            case ExtractVacancyImportResult.SessionNotFound ignored -> new ContinueVacancyImportResult.NoActiveSession();
        };
    }

    /**
     * Runs (or replays) AI extraction for a session. See the class-level Javadoc for the
     * transaction shape: the {@link VacancyExtractionPort} call below sits between two
     * independent, short {@link TransactionTemplate} blocks, never inside one.
     */
    @Override
    public ExtractVacancyImportResult extract(UUID sessionId) {
        Optional<VacancyImportSession> maybeSession = repository.findSessionById(sessionId);
        if (maybeSession.isEmpty()) {
            return new ExtractVacancyImportResult.SessionNotFound(sessionId);
        }
        VacancyImportSession session = maybeSession.get();
        return switch (session.getState()) {
            case EXTRACTING -> extractForExtractingSession(session);
            case WAITING_FOR_CONFIRMATION -> draftRepository.findDraftBySessionId(sessionId)
                    .<ExtractVacancyImportResult>map(draft -> new ExtractVacancyImportResult.AlreadyRecognized(sessionId, draft))
                    .orElseThrow(() -> new IllegalStateException(
                            "Session " + sessionId + " is WAITING_FOR_CONFIRMATION but has no draft"));
            default -> new ExtractVacancyImportResult.InvalidState(sessionId, session.getState());
        };
    }

    /**
     * A session already in {@code EXTRACTING} either has no draft yet (the common case - run
     * extraction) or already has one because a concurrent {@link #extract} call won the race
     * before this call's own draft insert (see {@link #persistDraftAndTransition}); the latter is
     * reported back as {@code AlreadyRecognized} without a further AI call.
     */
    private ExtractVacancyImportResult extractForExtractingSession(VacancyImportSession session) {
        UUID sessionId = session.getId();
        Optional<VacancyImportDraft> existingDraft = draftRepository.findDraftBySessionId(sessionId);
        if (existingDraft.isPresent()) {
            return new ExtractVacancyImportResult.AlreadyRecognized(sessionId, existingDraft.get());
        }

        ExtractedVacancyData validated;
        try {
            validated = extractionService.extract(VacancyExtractionRequest.ofPastedDescription(session.getRawDescription()));
        } catch (VacancyExtractionException failure) {
            log.warn("Vacancy extraction failed for session {}", sessionId, failure);
            session.fail(clock);
            return failExtraction(session);
        }

        session.markExtractionSucceeded(clock);
        return persistDraftAndTransition(session, validated);
    }

    /**
     * Inserts the draft and transitions {@code EXTRACTING -> WAITING_FOR_CONFIRMATION} in one
     * transaction, so the two either both apply or neither does: if the transition can't apply
     * (the session moved on for some other reason between the read above and this write), the
     * transaction is rolled back rather than leaving a draft attached to a session that isn't
     * {@code WAITING_FOR_CONFIRMATION}. Both writes use {@code session.getUpdatedAt()} - the
     * domain object's own clock-derived timestamp from {@link VacancyImportSession#markExtractionSucceeded},
     * set just before this is called - so the draft and the transition are stamped identically.
     */
    private ExtractVacancyImportResult persistDraftAndTransition(VacancyImportSession session, ExtractedVacancyData data) {
        UUID sessionId = session.getId();
        Instant updatedAt = session.getUpdatedAt();
        try {
            PersistOutcome outcome = newTransaction.execute(status -> {
                VacancyImportDraft draft = draftRepository.saveDraft(sessionId, data, updatedAt);
                boolean transitioned = repository.moveToWaitingForConfirmationIfExtracting(sessionId, updatedAt);
                if (!transitioned) {
                    status.setRollbackOnly();
                }
                return new PersistOutcome(draft, transitioned);
            });
            if (outcome.transitioned()) {
                return new ExtractVacancyImportResult.Recognized(sessionId, outcome.draft());
            }
            return resolveLostTransitionRace(sessionId);
        } catch (DataIntegrityViolationException lostDraftRace) {
            // A concurrent extract() call already inserted the draft for this session between our
            // existence check and our insert; load and return its draft instead of retrying.
            return draftRepository.findDraftBySessionId(sessionId)
                    .<ExtractVacancyImportResult>map(draft -> new ExtractVacancyImportResult.AlreadyRecognized(sessionId, draft))
                    .orElseGet(() -> new ExtractVacancyImportResult.Failed(sessionId));
        }
    }

    private ExtractVacancyImportResult resolveLostTransitionRace(UUID sessionId) {
        Optional<VacancyImportSession> current = repository.findSessionById(sessionId);
        if (current.isPresent() && current.get().getState() == ImportState.WAITING_FOR_CONFIRMATION) {
            return draftRepository.findDraftBySessionId(sessionId)
                    .<ExtractVacancyImportResult>map(draft -> new ExtractVacancyImportResult.AlreadyRecognized(sessionId, draft))
                    .orElseGet(() -> new ExtractVacancyImportResult.Failed(sessionId));
        }
        return new ExtractVacancyImportResult.Failed(sessionId);
    }

    /**
     * Moves a session from {@code EXTRACTING} to {@code FAILED} after a provider or validation
     * failure. If the conditional update applies to zero rows, another operation already resolved
     * this session (typically a concurrent successful extraction) - that outcome is not
     * overwritten with {@code FAILED}.
     */
    private ExtractVacancyImportResult failExtraction(VacancyImportSession session) {
        UUID sessionId = session.getId();
        Instant updatedAt = session.getUpdatedAt();
        boolean movedToFailed = newTransaction.execute(status -> repository.moveToFailedIfExtracting(sessionId, updatedAt));
        if (!movedToFailed) {
            return draftRepository.findDraftBySessionId(sessionId)
                    .<ExtractVacancyImportResult>map(draft -> new ExtractVacancyImportResult.AlreadyRecognized(sessionId, draft))
                    .orElseGet(() -> new ExtractVacancyImportResult.Failed(sessionId));
        }
        return new ExtractVacancyImportResult.Failed(sessionId);
    }

    private record PersistOutcome(VacancyImportDraft draft, boolean transitioned) {
    }

    private void expireAndPersist(VacancyImportSession session) {
        newTransaction.executeWithoutResult(status -> {
            session.expire(clock);
            repository.saveSession(session);
        });
    }

    private StartVacancyImportResult startNewSession(long telegramChatId, long telegramUserId) {
        VacancyImportSession session = VacancyImportSession.start(telegramChatId, telegramUserId, clock, properties.sessionTtl());
        try {
            VacancyImportSession saved = newTransaction.execute(status -> repository.saveAndFlushSession(session));
            return new StartVacancyImportResult.Started(saved.getId());
        } catch (DataIntegrityViolationException lostStartRace) {
            return repository.findActiveSession(telegramChatId, telegramUserId)
                    .<StartVacancyImportResult>map(existing -> new StartVacancyImportResult.AlreadyActive(existing.getId(), existing.getState()))
                    .orElseThrow(() -> lostStartRace);
        }
    }

    private sealed interface ActiveSessionLookup {
        record Found(VacancyImportSession session) implements ActiveSessionLookup {
        }

        record NotFound() implements ActiveSessionLookup {
        }

        record Expired() implements ActiveSessionLookup {
        }
    }

    private sealed interface UrlAcceptanceOutcome {
        record Accepted(URI normalizedUrl) implements UrlAcceptanceOutcome {
        }

        record Invalid(String reason) implements UrlAcceptanceOutcome {
        }
    }
}
