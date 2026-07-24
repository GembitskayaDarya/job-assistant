package com.darya.jobassistant.vacancyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyimport.config.VacancyImportCleanupProperties;
import com.darya.jobassistant.vacancyimport.dto.ExpireVacancyImportSessionsResult;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class VacancyImportExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int BATCH_SIZE = 100;

    @Mock
    private VacancyImportSessionRepository sessionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VacancyImportExpirationService service;

    @BeforeEach
    void setUp() {
        VacancyImportCleanupProperties properties =
                new VacancyImportCleanupProperties(true, Duration.ofHours(1), Duration.ofMinutes(1), BATCH_SIZE);
        service = new VacancyImportExpirationService(sessionRepository, CLOCK, properties, transactionManager);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    }

    @Test
    void expireBatch_noExpiredSessions_returnsZeroCounts() {
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of());

        ExpireVacancyImportSessionsResult result = service.expireBatch();

        assertThat(result).isEqualTo(ExpireVacancyImportSessionsResult.empty());
        verify(sessionRepository, never()).expireIfActiveAndExpired(any(), any(), any());
    }

    @Test
    void expireBatch_oneExpiredActiveSession_isConditionallyExpired() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of(sessionId));
        when(sessionRepository.expireIfActiveAndExpired(sessionId, NOW, NOW)).thenReturn(true);

        ExpireVacancyImportSessionsResult result = service.expireBatch();

        assertThat(result).isEqualTo(new ExpireVacancyImportSessionsResult(1, 1, 0, 0));
        verify(sessionRepository).expireIfActiveAndExpired(sessionId, NOW, NOW);
    }

    @Test
    void expireBatch_multipleSessions_areAllProcessedUpToBatchSize() {
        List<UUID> sessionIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(sessionIds);
        for (UUID sessionId : sessionIds) {
            when(sessionRepository.expireIfActiveAndExpired(sessionId, NOW, NOW)).thenReturn(true);
        }

        ExpireVacancyImportSessionsResult result = service.expireBatch();

        assertThat(result).isEqualTo(new ExpireVacancyImportSessionsResult(3, 3, 0, 0));
        for (UUID sessionId : sessionIds) {
            verify(sessionRepository).expireIfActiveAndExpired(sessionId, NOW, NOW);
        }
    }

    @Test
    void expireBatch_usesTheDeterministicInjectedClockForBothSelectionAndExpiration() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of(sessionId));
        when(sessionRepository.expireIfActiveAndExpired(sessionId, NOW, NOW)).thenReturn(true);

        service.expireBatch();

        verify(sessionRepository).findExpiredActiveSessionIds(eq(NOW), eq(BATCH_SIZE));
        verify(sessionRepository).expireIfActiveAndExpired(sessionId, NOW, NOW);
    }

    @Test
    void expireBatch_concurrentStateChange_producesASkippedResultNotAFailure() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of(sessionId));
        when(sessionRepository.expireIfActiveAndExpired(sessionId, NOW, NOW)).thenReturn(false);

        ExpireVacancyImportSessionsResult result = service.expireBatch();

        assertThat(result).isEqualTo(new ExpireVacancyImportSessionsResult(1, 0, 1, 0));
    }

    @Test
    void expireBatch_repositoryFailureForOneSession_isCountedAsFailedAndOthersStillProcessed() {
        UUID failingSessionId = UUID.randomUUID();
        UUID healthySessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of(failingSessionId, healthySessionId));
        when(sessionRepository.expireIfActiveAndExpired(failingSessionId, NOW, NOW))
                .thenThrow(new RuntimeException("simulated database failure"));
        when(sessionRepository.expireIfActiveAndExpired(healthySessionId, NOW, NOW)).thenReturn(true);

        ExpireVacancyImportSessionsResult result = service.expireBatch();

        assertThat(result).isEqualTo(new ExpireVacancyImportSessionsResult(2, 1, 0, 1));
    }

    @Test
    void expireBatch_touchesOnlyTheConditionalExpirationOperationNeverAnUnconditionalWrite() {
        // Terminal-state exclusion is enforced by findExpiredActiveSessionIds/expireIfActiveAndExpired
        // themselves (verified at the repository level); this service is structurally incapable of
        // deleting a draft or Vacancy or mutating a session any other way - its only dependency is
        // VacancyImportSessionRepository, and the only write it ever calls is expireIfActiveAndExpired.
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findExpiredActiveSessionIds(NOW, BATCH_SIZE)).thenReturn(List.of(sessionId));
        when(sessionRepository.expireIfActiveAndExpired(sessionId, NOW, NOW)).thenReturn(true);

        service.expireBatch();

        verify(sessionRepository, never()).saveSession(any());
        verify(sessionRepository, never()).completeIfWaitingForConfirmation(any(), any(), any());
        verify(sessionRepository, never()).cancelIfWaitingForConfirmation(any(), any());
        verify(sessionRepository).expireIfActiveAndExpired(sessionId, NOW, NOW);
    }

    @Test
    void expireBatch_passesConfiguredBatchSizeToTheRepositoryQuery() {
        VacancyImportCleanupProperties customProperties =
                new VacancyImportCleanupProperties(true, Duration.ofHours(1), Duration.ofMinutes(1), 7);
        VacancyImportExpirationService customService =
                new VacancyImportExpirationService(sessionRepository, CLOCK, customProperties, transactionManager);
        when(sessionRepository.findExpiredActiveSessionIds(NOW, 7)).thenReturn(List.of());

        customService.expireBatch();

        verify(sessionRepository).findExpiredActiveSessionIds(NOW, 7);
    }
}
