package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.config.VacancyImportCleanupProperties;
import com.darya.jobassistant.vacancyimport.dto.ExpireVacancyImportSessionsResult;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expires one bounded batch of abandoned active {@code VacancyImportSession} rows per call. Kept
 * separate from {@link VacancyImportService} and {@link VacancyImportReviewService}: this is a
 * scheduler-triggered batch sweep over many sessions, not a single-session operation triggered by
 * a specific user action, and shares no meaningful code with either.
 *
 * <p>No class-level {@code @Transactional}, for the same reason as the other services in this
 * package: candidate selection and each session's conditional expiration are independent, short
 * {@link TransactionTemplate} calls rather than one transaction spanning the whole batch - a batch
 * of (bounded, but still many) row updates has no need to hold one lock-accumulating transaction
 * open across all of them, and a failure expiring one session must not roll back the others.
 */
@Slf4j
@Service
public class VacancyImportExpirationService implements ExpireVacancyImportSessionsUseCase {

    private final VacancyImportSessionRepository repository;
    private final Clock clock;
    private final VacancyImportCleanupProperties properties;
    private final TransactionTemplate newTransaction;

    public VacancyImportExpirationService(
            VacancyImportSessionRepository repository,
            Clock clock,
            VacancyImportCleanupProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public ExpireVacancyImportSessionsResult expireBatch() {
        Instant now = Instant.now(clock);
        List<UUID> candidates = newTransaction.execute(status -> repository.findExpiredActiveSessionIds(now, properties.batchSize()));
        if (candidates.isEmpty()) {
            return ExpireVacancyImportSessionsResult.empty();
        }

        int expired = 0;
        int skipped = 0;
        int failed = 0;
        for (UUID sessionId : candidates) {
            try {
                boolean applied = newTransaction.execute(status -> repository.expireIfActiveAndExpired(sessionId, now, now));
                if (applied) {
                    expired++;
                } else {
                    // Normal concurrency skip: the user acted on this session (or another
                    // operation resolved it) between selection and this update - not a failure.
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Failed to expire vacancy import session {}", sessionId, e);
            }
        }
        return new ExpireVacancyImportSessionsResult(candidates.size(), expired, skipped, failed);
    }
}
