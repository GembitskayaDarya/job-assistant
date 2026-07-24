package com.darya.jobassistant.vacancyimport.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class VacancyImportSessionRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    private VacancyImportSessionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveSession_thenFindSessionById_returnsEquivalentSession() {
        VacancyImportSession session = VacancyImportSession.start(1L, 2L, CLOCK, TTL);

        VacancyImportSession saved = repository.saveSession(session);
        entityManager.flush();
        entityManager.clear();

        Optional<VacancyImportSession> loaded = repository.findSessionById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getTelegramChatId()).isEqualTo(1L);
        assertThat(loaded.get().getTelegramUserId()).isEqualTo(2L);
        assertThat(loaded.get().getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(loaded.get().getExpiresAt()).isEqualTo(session.getExpiresAt());
    }

    @Test
    void findActiveSession_returnsActiveSessionForChatAndUser() {
        repository.saveSession(VacancyImportSession.start(10L, 20L, CLOCK, TTL));
        entityManager.flush();

        Optional<VacancyImportSession> active = repository.findActiveSession(10L, 20L);

        assertThat(active).isPresent();
        assertThat(active.get().getTelegramChatId()).isEqualTo(10L);
        assertThat(active.get().getTelegramUserId()).isEqualTo(20L);
    }

    @Test
    void findActiveSession_noActiveSession_returnsEmpty() {
        Optional<VacancyImportSession> active = repository.findActiveSession(999L, 999L);

        assertThat(active).isEmpty();
    }

    @Test
    void state_isPersistedAsString() {
        VacancyImportSession saved = repository.saveSession(VacancyImportSession.start(30L, 40L, CLOCK, TTL));
        entityManager.flush();
        entityManager.clear();

        Object rawState = entityManager
                .createNativeQuery("SELECT state FROM vacancy_import_session WHERE id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();

        assertThat(rawState).isEqualTo("WAITING_FOR_URL");
    }

    @Test
    void rawDescription_isPersistedWithoutTruncation() {
        VacancyImportSession session = VacancyImportSession.start(50L, 60L, CLOCK, TTL);
        session.provideUrl("https://example.com/job", CLOCK);
        String longDescription = "A".repeat(20_000);
        session.provideDescriptionAndStartExtraction(longDescription, CLOCK);

        VacancyImportSession saved = repository.saveSession(session);
        entityManager.flush();
        entityManager.clear();

        VacancyImportSession loaded = repository.findSessionById(saved.getId()).orElseThrow();
        assertThat(loaded.getRawDescription()).hasSize(20_000);
        assertThat(loaded.getRawDescription()).isEqualTo(longDescription);
    }

    @Test
    void timestampsAndExpiration_arePersistedAndReloaded() {
        VacancyImportSession session = VacancyImportSession.start(70L, 80L, CLOCK, TTL);

        VacancyImportSession saved = repository.saveSession(session);
        entityManager.flush();
        entityManager.clear();

        VacancyImportSession loaded = repository.findSessionById(saved.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getExpiresAt()).isEqualTo(session.getExpiresAt());
    }

    @Test
    void savingSecondActiveSessionForSameChatAndUser_violatesPartialUniqueIndex() {
        repository.saveSession(VacancyImportSession.start(90L, 100L, CLOCK, TTL));
        entityManager.flush();

        repository.saveSession(VacancyImportSession.start(90L, 100L, CLOCK, TTL));

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void newActiveSession_isAllowedAfterPreviousSessionBecomesTerminal() {
        VacancyImportSession first = repository.saveSession(VacancyImportSession.start(200L, 300L, CLOCK, TTL));
        entityManager.flush();

        VacancyImportSession toCancel = repository.findSessionById(first.getId()).orElseThrow();
        toCancel.cancel(CLOCK);
        repository.saveSession(toCancel);
        entityManager.flush();

        VacancyImportSession second = repository.saveSession(VacancyImportSession.start(200L, 300L, CLOCK, TTL));
        entityManager.flush();

        assertThat(second.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(repository.findActiveSession(200L, 300L)).isPresent();
        assertThat(repository.findActiveSession(200L, 300L).get().getId()).isEqualTo(second.getId());
    }

    @Test
    void differentTelegramUsersInSameChat_canEachHaveAnActiveSession() {
        repository.saveSession(VacancyImportSession.start(400L, 1L, CLOCK, TTL));
        repository.saveSession(VacancyImportSession.start(400L, 2L, CLOCK, TTL));
        entityManager.flush();

        assertThat(repository.findActiveSession(400L, 1L)).isPresent();
        assertThat(repository.findActiveSession(400L, 2L)).isPresent();
    }

    @Test
    void differentChats_canEachHaveAnActiveSessionForSameUser() {
        repository.saveSession(VacancyImportSession.start(500L, 9L, CLOCK, TTL));
        repository.saveSession(VacancyImportSession.start(600L, 9L, CLOCK, TTL));
        entityManager.flush();

        assertThat(repository.findActiveSession(500L, 9L)).isPresent();
        assertThat(repository.findActiveSession(600L, 9L)).isPresent();
    }

    @Test
    void findExpiredActiveSessions_returnsOnlyActiveSessionsPastExpiration() {
        Clock past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        VacancyImportSession expired = repository.saveSession(
                VacancyImportSession.start(700L, 800L, past, Duration.ofMinutes(1)));
        VacancyImportSession stillFresh = repository.saveSession(VacancyImportSession.start(710L, 810L, CLOCK, TTL));
        entityManager.flush();

        var expiredSessions = repository.findExpiredActiveSessions(Instant.now(CLOCK));

        assertThat(expiredSessions).extracting(VacancyImportSession::getId).contains(expired.getId());
        assertThat(expiredSessions).extracting(VacancyImportSession::getId).doesNotContain(stillFresh.getId());
    }

    @Test
    void moveToWaitingForConfirmationIfExtracting_sessionInExtracting_updatesExactlyOneRow() {
        VacancyImportSession session = sessionAtExtracting(900L, 901L);
        Instant updatedAt = CLOCK.instant().plusSeconds(5);

        boolean applied = repository.moveToWaitingForConfirmationIfExtracting(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void moveToWaitingForConfirmationIfExtracting_sessionNotInExtracting_updatesNoRowsAndDoesNotOverwriteState() {
        VacancyImportSession session = repository.saveSession(VacancyImportSession.start(910L, 911L, CLOCK, TTL));
        entityManager.flush();

        boolean applied = repository.moveToWaitingForConfirmationIfExtracting(session.getId(), CLOCK.instant());
        entityManager.clear();

        assertThat(applied).isFalse();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
    }

    @Test
    void moveToFailedIfExtracting_sessionInExtracting_updatesExactlyOneRow() {
        VacancyImportSession session = sessionAtExtracting(920L, 921L);
        Instant updatedAt = CLOCK.instant().plusSeconds(5);

        boolean applied = repository.moveToFailedIfExtracting(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.FAILED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void moveToFailedIfExtracting_sessionAlreadyWaitingForConfirmation_doesNotOverwriteTheWinningState() {
        VacancyImportSession session = sessionAtExtracting(930L, 931L);
        boolean firstTransitionApplied =
                repository.moveToWaitingForConfirmationIfExtracting(session.getId(), CLOCK.instant().plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        boolean secondTransitionApplied =
                repository.moveToFailedIfExtracting(session.getId(), CLOCK.instant().plusSeconds(2));
        entityManager.clear();

        assertThat(firstTransitionApplied).isTrue();
        assertThat(secondTransitionApplied).isFalse();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
    }

    private VacancyImportSession sessionAtExtracting(long chatId, long userId) {
        VacancyImportSession session = VacancyImportSession.start(chatId, userId, CLOCK, TTL);
        session.provideUrl("https://example.com/job", CLOCK);
        session.provideDescriptionAndStartExtraction("A perfectly valid vacancy description here.", CLOCK);
        VacancyImportSession saved = repository.saveSession(session);
        entityManager.flush();
        return saved;
    }
}
