package com.darya.jobassistant.vacancyimport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.vacancyimport.exception.VacancyImportSessionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyImportSessionTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final long CHAT_ID = 111L;
    private static final long USER_ID = 222L;
    private static final String VALID_URL = "https://example.com/jobs/123";
    private static final String VALID_DESCRIPTION =
            "We are looking for a Senior Java Backend Engineer with Kafka experience.";

    @Test
    void start_createsSessionInWaitingForUrlState() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
    }

    @Test
    void start_initializesIdentifiersAndTimestamps() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThat(session.getId()).isNotNull();
        assertThat(session.getTelegramChatId()).isEqualTo(CHAT_ID);
        assertThat(session.getTelegramUserId()).isEqualTo(USER_ID);
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getUpdatedAt()).isEqualTo(NOW);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(session.getSourceUrl()).isNull();
        assertThat(session.getRawDescription()).isNull();
    }

    @Test
    void provideUrl_validHttpsUrl_transitionsToWaitingForDescription() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        session.provideUrl(VALID_URL, CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(session.getSourceUrl()).isEqualTo(VALID_URL);
    }

    @Test
    void provideUrl_nonHttpScheme_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.provideUrl("ftp://example.com/file", CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideUrl_relativeUrl_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.provideUrl("/jobs/123", CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideUrl_null_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.provideUrl(null, CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideUrl_wrongState_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        session.provideUrl(VALID_URL, CLOCK);

        assertThatThrownBy(() -> session.provideUrl(VALID_URL, CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideDescriptionAndStartExtraction_validDescription_transitionsToExtracting() {
        VacancyImportSession session = sessionAtWaitingForDescription();

        session.provideDescriptionAndStartExtraction(VALID_DESCRIPTION, CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.EXTRACTING);
        assertThat(session.getRawDescription()).isEqualTo(VALID_DESCRIPTION);
    }

    @Test
    void provideDescriptionAndStartExtraction_blank_isRejected() {
        VacancyImportSession session = sessionAtWaitingForDescription();

        assertThatThrownBy(() -> session.provideDescriptionAndStartExtraction("   ", CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideDescriptionAndStartExtraction_tooShort_isRejected() {
        VacancyImportSession session = sessionAtWaitingForDescription();

        assertThatThrownBy(() -> session.provideDescriptionAndStartExtraction("short", CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void provideDescriptionAndStartExtraction_wrongState_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.provideDescriptionAndStartExtraction(VALID_DESCRIPTION, CLOCK))
                .isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void markExtractionSucceeded_transitionsToWaitingForConfirmation() {
        VacancyImportSession session = sessionAtExtracting();

        session.markExtractionSucceeded(CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
    }

    @Test
    void retryDescription_transitionsBackToWaitingForDescriptionAndClearsDescriptionButKeepsUrl() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();

        session.retryDescription(CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(session.getRawDescription()).isNull();
        assertThat(session.getSourceUrl()).isEqualTo(VALID_URL);
    }

    @Test
    void complete_transitionsToCompletedAndStoresVacancyId() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID vacancyId = UUID.randomUUID();

        session.complete(vacancyId, CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.COMPLETED);
        assertThat(session.getVacancyId()).isEqualTo(vacancyId);
    }

    @Test
    void complete_wrongState_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.complete(UUID.randomUUID(), CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void complete_nullVacancyId_isRejected() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();

        assertThatThrownBy(() -> session.complete(null, CLOCK)).isInstanceOf(VacancyImportSessionException.class);
        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
    }

    @Test
    void cancel_fromWaitingForUrl_transitionsToCancelled() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        session.cancel(CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.CANCELLED);
    }

    @Test
    void cancel_fromWaitingForConfirmation_transitionsToCancelled() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();

        session.cancel(CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.CANCELLED);
    }

    @Test
    void cancel_fromExtracting_isRejected() {
        VacancyImportSession session = sessionAtExtracting();

        assertThatThrownBy(() -> session.cancel(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void fail_fromExtracting_transitionsToFailed() {
        VacancyImportSession session = sessionAtExtracting();

        session.fail(CLOCK);

        assertThat(session.getState()).isEqualTo(ImportState.FAILED);
    }

    @Test
    void fail_fromNonExtractingState_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.fail(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void expire_afterExpirationTime_transitionsToExpired() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        Clock afterExpiration = Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC);

        session.expire(afterExpiration);

        assertThat(session.getState()).isEqualTo(ImportState.EXPIRED);
    }

    @Test
    void expire_beforeExpirationTime_isRejected() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);

        assertThatThrownBy(() -> session.expire(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void expire_fromExtracting_isRejectedEvenIfPastExpirationTime() {
        VacancyImportSession session = sessionAtExtracting();
        Clock afterExpiration = Clock.fixed(NOW.plus(TTL).plusSeconds(1), ZoneOffset.UTC);

        assertThatThrownBy(() -> session.expire(afterExpiration)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void terminalState_completed_rejectsAnyFurtherTransition() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        session.complete(UUID.randomUUID(), CLOCK);

        assertThatThrownBy(() -> session.cancel(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
        assertThatThrownBy(() -> session.retryDescription(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
        assertThatThrownBy(() -> session.complete(UUID.randomUUID(), CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void terminalState_cancelled_rejectsAnyFurtherTransition() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        session.cancel(CLOCK);

        assertThatThrownBy(() -> session.provideUrl(VALID_URL, CLOCK)).isInstanceOf(VacancyImportSessionException.class);
        assertThatThrownBy(() -> session.cancel(CLOCK)).isInstanceOf(VacancyImportSessionException.class);
    }

    @Test
    void isActive_reflectsUnderlyingState() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        assertThat(session.isActive()).isTrue();

        session.cancel(CLOCK);
        assertThat(session.isActive()).isFalse();
    }

    private VacancyImportSession sessionAtWaitingForDescription() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        session.provideUrl(VALID_URL, CLOCK);
        return session;
    }

    private VacancyImportSession sessionAtExtracting() {
        VacancyImportSession session = sessionAtWaitingForDescription();
        session.provideDescriptionAndStartExtraction(VALID_DESCRIPTION, CLOCK);
        return session;
    }

    private VacancyImportSession sessionAtWaitingForConfirmation() {
        VacancyImportSession session = sessionAtExtracting();
        session.markExtractionSucceeded(CLOCK);
        return session;
    }
}
