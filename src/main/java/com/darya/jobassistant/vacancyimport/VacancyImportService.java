package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.config.VacancyImportProperties;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.exception.VacancyImportSessionException;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import com.darya.jobassistant.vacancyimport.url.VacancyUrlValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 */
@Service
public class VacancyImportService
        implements StartVacancyImportUseCase, CancelVacancyImportUseCase, ProvideVacancyUrlUseCase, ContinueVacancyImportUseCase {

    private final VacancyImportSessionRepository repository;
    private final Clock clock;
    private final VacancyImportProperties properties;
    private final TransactionTemplate newTransaction;

    public VacancyImportService(
            VacancyImportSessionRepository repository,
            Clock clock,
            VacancyImportProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
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
        return new ContinueVacancyImportResult.DescriptionAccepted(session.getId());
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
