package com.darya.jobassistant.vacancyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyimport.config.VacancyImportProperties;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class VacancyImportServiceTest {

    private static final long CHAT_ID = 111L;
    private static final long USER_ID = 222L;
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(24);

    @Mock
    private VacancyImportSessionRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VacancyImportProperties properties;
    private VacancyImportService service;

    @BeforeEach
    void setUp() {
        properties = new VacancyImportProperties(TTL);
        service = new VacancyImportService(repository, CLOCK, properties, transactionManager);
    }

    @Test
    void start_noActiveSession_createsSessionInWaitingForUrlUsingConfiguredTtl() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.empty());
        when(repository.saveAndFlushSession(any(VacancyImportSession.class))).thenAnswer(inv -> inv.getArgument(0));

        StartVacancyImportResult result = service.start(CHAT_ID, USER_ID);

        ArgumentCaptor<VacancyImportSession> captor = ArgumentCaptor.forClass(VacancyImportSession.class);
        verify(repository).saveAndFlushSession(captor.capture());
        VacancyImportSession persisted = captor.getValue();
        assertThat(persisted.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(persisted.getTelegramChatId()).isEqualTo(CHAT_ID);
        assertThat(persisted.getTelegramUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(result).isInstanceOf(StartVacancyImportResult.Started.class);
        assertThat(((StartVacancyImportResult.Started) result).sessionId()).isEqualTo(persisted.getId());
    }

    @Test
    void start_activeSessionExists_doesNotCreateSecondSessionAndReturnsItsState() {
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        active.provideUrl("https://example.com/job", CLOCK);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        StartVacancyImportResult result = service.start(CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(new StartVacancyImportResult.AlreadyActive(active.getId(), ImportState.WAITING_FOR_DESCRIPTION));
        verify(repository, never()).saveSession(any());
        verify(repository, never()).saveAndFlushSession(any());
    }

    @Test
    void start_expiredActiveSession_expiresItAndCreatesNewSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Clock pastClock = Clock.fixed(NOW.minus(Duration.ofHours(25)), ZoneOffset.UTC);
        VacancyImportSession expired = VacancyImportSession.start(CHAT_ID, USER_ID, pastClock, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(expired));
        when(repository.saveSession(expired)).thenReturn(expired);
        when(repository.saveAndFlushSession(any(VacancyImportSession.class))).thenAnswer(inv -> inv.getArgument(0));

        StartVacancyImportResult result = service.start(CHAT_ID, USER_ID);

        assertThat(expired.getState()).isEqualTo(ImportState.EXPIRED);
        verify(repository).saveSession(expired);
        ArgumentCaptor<VacancyImportSession> captor = ArgumentCaptor.forClass(VacancyImportSession.class);
        verify(repository).saveAndFlushSession(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(result).isInstanceOf(StartVacancyImportResult.Started.class);
    }

    @Test
    void start_uniquenessRaceLost_returnsAlreadyActiveWithTheWinningSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(repository.findActiveSession(CHAT_ID, USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerSession()));
        when(repository.saveAndFlushSession(any(VacancyImportSession.class)))
                .thenThrow(new DataIntegrityViolationException("uk_vacancy_import_session_active_chat_user"));

        StartVacancyImportResult result = service.start(CHAT_ID, USER_ID);

        assertThat(result).isInstanceOf(StartVacancyImportResult.AlreadyActive.class);
        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void cancel_activeSession_cancelsThroughDomainOperationAndPersists() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        CancelVacancyImportResult result = service.cancel(CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(CancelVacancyImportResult.CANCELLED);
        assertThat(active.getState()).isEqualTo(ImportState.CANCELLED);
        verify(repository).saveSession(active);
    }

    @Test
    void cancel_noActiveSession_returnsNoActiveSessionWithoutPersisting() {
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.empty());

        CancelVacancyImportResult result = service.cancel(CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(CancelVacancyImportResult.NO_ACTIVE_SESSION);
        verify(repository, never()).saveSession(any());
    }

    @Test
    void cancel_calledTwice_secondCallIsSafeAndReportsNoActiveSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.empty());

        CancelVacancyImportResult first = service.cancel(CHAT_ID, USER_ID);
        CancelVacancyImportResult second = service.cancel(CHAT_ID, USER_ID);

        assertThat(first).isEqualTo(CancelVacancyImportResult.CANCELLED);
        assertThat(second).isEqualTo(CancelVacancyImportResult.NO_ACTIVE_SESSION);
    }

    @Test
    void provideUrl_waitingForUrlAndValidUrl_transitionsToWaitingForDescriptionAndPersistsNormalizedUrl() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ProvideVacancyUrlResult result = service.provideUrl(CHAT_ID, USER_ID, "HTTPS://Example.com/job/123#top");

        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(active.getSourceUrl()).isEqualTo("https://example.com/job/123");
        assertThat(result).isEqualTo(new ProvideVacancyUrlResult.Accepted(active.getId(), URI.create("https://example.com/job/123")));
        verify(repository).saveSession(active);
    }

    @Test
    void provideUrl_invalidUrl_doesNotMutateSessionOrPersist() {
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ProvideVacancyUrlResult result = service.provideUrl(CHAT_ID, USER_ID, "not a url at all");

        assertThat(result).isInstanceOf(ProvideVacancyUrlResult.InvalidUrl.class);
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(active.getSourceUrl()).isNull();
        verify(repository, never()).saveSession(any());
    }

    @Test
    void provideUrl_noActiveSession_returnsNoActiveSession() {
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.empty());

        ProvideVacancyUrlResult result = service.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123");

        assertThat(result).isEqualTo(new ProvideVacancyUrlResult.NoActiveSession());
        verify(repository, never()).saveSession(any());
    }

    @Test
    void provideUrl_expiredActiveSession_isPersistedAsExpiredAndDoesNotAutoRestart() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Clock pastClock = Clock.fixed(NOW.minus(Duration.ofHours(25)), ZoneOffset.UTC);
        VacancyImportSession expired = VacancyImportSession.start(CHAT_ID, USER_ID, pastClock, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(expired));
        when(repository.saveSession(expired)).thenReturn(expired);

        ProvideVacancyUrlResult result = service.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123");

        assertThat(result).isEqualTo(new ProvideVacancyUrlResult.SessionExpired());
        assertThat(expired.getState()).isEqualTo(ImportState.EXPIRED);
        verify(repository).saveSession(expired);
        verify(repository, never()).saveAndFlushSession(any());
    }

    @Test
    void provideUrl_unexpectedActiveState_isNotMutatedAndReturnsItsState() {
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        active.provideUrl("https://example.com/job/123", CLOCK);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ProvideVacancyUrlResult result = service.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/456");

        assertThat(result).isEqualTo(new ProvideVacancyUrlResult.UnexpectedState(ImportState.WAITING_FOR_DESCRIPTION));
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(active.getSourceUrl()).isEqualTo("https://example.com/job/123");
        verify(repository, never()).saveSession(any());
    }

    @Test
    void handleText_waitingForUrl_validUrl_routesThroughUrlAcceptanceAndTransitions() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "https://example.com/job/123");

        assertThat(result).isEqualTo(
                new ContinueVacancyImportResult.UrlAccepted(active.getId(), URI.create("https://example.com/job/123")));
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        verify(repository).saveSession(active);
    }

    @Test
    void handleText_waitingForUrl_invalidUrl_leavesSessionUnchanged() {
        VacancyImportSession active = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "not a url");

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.InvalidUrl.class);
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        verify(repository, never()).saveSession(any());
        verify(repository, never()).acceptDescriptionIfWaiting(any(), any(), any());
    }

    @Test
    void handleText_waitingForDescription_validDescription_acceptsAndTransitionsToExtracting() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = sessionAtWaitingForDescription();
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));
        when(repository.acceptDescriptionIfWaiting(any(), any(), any())).thenReturn(true);
        String description = "  We need a Senior Java Backend Engineer.\n\n"
                + "- 6+ years experience\n- Kafka, Redis, AWS\n\n"
                + "Wymagania: znajomość Javy. Требования: опыт работы с Kafka.  ";

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, description);

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.DescriptionAccepted(active.getId()));
        assertThat(active.getState()).isEqualTo(ImportState.EXTRACTING);
        assertThat(active.getRawDescription()).isEqualTo(description.trim());
        assertThat(active.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).acceptDescriptionIfWaiting(active.getId(), description.trim(), NOW);
    }

    @Test
    void handleText_waitingForDescription_blankText_isRejectedAndDoesNotMutateOrPersist() {
        VacancyImportSession active = sessionAtWaitingForDescription();
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, null);

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.InvalidDescription.class);
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(active.getRawDescription()).isNull();
        verify(repository, never()).acceptDescriptionIfWaiting(any(), any(), any());
    }

    @Test
    void handleText_waitingForDescription_whitespaceOnlyText_isRejected() {
        VacancyImportSession active = sessionAtWaitingForDescription();
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "   \n  ");

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.InvalidDescription.class);
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        verify(repository, never()).acceptDescriptionIfWaiting(any(), any(), any());
    }

    @Test
    void handleText_waitingForDescription_tooShortText_isRejectedByDomainRuleAndNotPersisted() {
        VacancyImportSession active = sessionAtWaitingForDescription();
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "too short");

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.InvalidDescription.class);
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(active.getRawDescription()).isNull();
        verify(repository, never()).acceptDescriptionIfWaiting(any(), any(), any());
    }

    @Test
    void handleText_extracting_doesNotAcceptAnotherDescriptionAndDoesNotMutate() {
        VacancyImportSession active = sessionAtWaitingForDescription();
        active.provideDescriptionAndStartExtraction("A perfectly valid description here.", CLOCK);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "another description attempt");

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.UnexpectedState(ImportState.EXTRACTING));
        assertThat(active.getState()).isEqualTo(ImportState.EXTRACTING);
        verify(repository, never()).saveSession(any());
        verify(repository, never()).acceptDescriptionIfWaiting(any(), any(), any());
    }

    @Test
    void handleText_waitingForConfirmation_doesNotAcceptOrdinaryTextAndDoesNotMutate() {
        VacancyImportSession active = sessionAtWaitingForDescription();
        active.provideDescriptionAndStartExtraction("A perfectly valid description here.", CLOCK);
        active.markExtractionSucceeded(CLOCK);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "confirm please");

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.UnexpectedState(ImportState.WAITING_FOR_CONFIRMATION));
        assertThat(active.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
        verify(repository, never()).saveSession(any());
    }

    @Test
    void handleText_noActiveSession_returnsNoActiveSession() {
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.empty());

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "hello");

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.NoActiveSession());
    }

    @Test
    void handleText_expiredSession_isPersistedAsExpired() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Clock pastClock = Clock.fixed(NOW.minus(Duration.ofHours(25)), ZoneOffset.UTC);
        VacancyImportSession expired = VacancyImportSession.start(CHAT_ID, USER_ID, pastClock, TTL);
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(expired));
        when(repository.saveSession(expired)).thenReturn(expired);

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "https://example.com/job/123");

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.SessionExpired());
        assertThat(expired.getState()).isEqualTo(ImportState.EXPIRED);
        verify(repository).saveSession(expired);
    }

    @Test
    void handleText_concurrentDescriptionRaceLost_returnsUnexpectedStateInsteadOfSilentlySucceeding() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession active = sessionAtWaitingForDescription();
        when(repository.findActiveSession(CHAT_ID, USER_ID)).thenReturn(Optional.of(active));
        when(repository.acceptDescriptionIfWaiting(any(), any(), any())).thenReturn(false);
        VacancyImportSession winnerNowExtracting = sessionAtWaitingForDescription();
        winnerNowExtracting.provideDescriptionAndStartExtraction("The other request's description.", CLOCK);
        when(repository.findSessionById(active.getId())).thenReturn(Optional.of(winnerNowExtracting));

        ContinueVacancyImportResult result = service.handleText(CHAT_ID, USER_ID, "my description attempt");

        assertThat(result).isEqualTo(new ContinueVacancyImportResult.UnexpectedState(ImportState.EXTRACTING));
    }

    private VacancyImportSession sessionAtWaitingForDescription() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        session.provideUrl("https://example.com/job/123", CLOCK);
        return session;
    }

    private VacancyImportSession winnerSession() {
        VacancyImportSession winner = mock(VacancyImportSession.class);
        when(winner.getId()).thenReturn(java.util.UUID.randomUUID());
        when(winner.getState()).thenReturn(ImportState.WAITING_FOR_URL);
        return winner;
    }
}
