package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.config.VacancyImportProperties;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
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
 */
@Service
public class VacancyImportService
        implements StartVacancyImportUseCase, CancelVacancyImportUseCase, ProvideVacancyUrlUseCase {

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
        Optional<VacancyImportSession> active = repository.findActiveSession(telegramChatId, telegramUserId);
        if (active.isEmpty()) {
            return new ProvideVacancyUrlResult.NoActiveSession();
        }
        VacancyImportSession session = active.get();
        if (!Instant.now(clock).isBefore(session.getExpiresAt())) {
            expireAndPersist(session);
            return new ProvideVacancyUrlResult.SessionExpired();
        }
        if (session.getState() != ImportState.WAITING_FOR_URL) {
            return new ProvideVacancyUrlResult.UnexpectedState(session.getState());
        }

        VacancyUrlValidator.Result validation = VacancyUrlValidator.validate(rawUrl);
        if (validation instanceof VacancyUrlValidator.Result.Invalid invalid) {
            return new ProvideVacancyUrlResult.InvalidUrl(invalid.reason());
        }
        URI normalizedUrl = ((VacancyUrlValidator.Result.Valid) validation).normalizedUrl();
        session.provideUrl(normalizedUrl.toString(), clock);
        newTransaction.executeWithoutResult(status -> repository.saveSession(session));
        return new ProvideVacancyUrlResult.Accepted(session.getId(), normalizedUrl);
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
}
