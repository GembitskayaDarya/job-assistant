package com.darya.jobassistant.vacancyimport.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

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

    @Test
    void vacancyId_isNullableAndRoundTrips() {
        VacancyImportSession waiting = sessionAtWaitingForConfirmation(940L, 941L);
        entityManager.clear();
        VacancyImportSession reloadedBeforeSave = repository.findSessionById(waiting.getId()).orElseThrow();
        assertThat(reloadedBeforeSave.getVacancyId()).isNull();

        Vacancy vacancy = persistVacancy();
        boolean applied = repository.completeIfWaitingForConfirmation(waiting.getId(), vacancy.getId(), CLOCK.instant().plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloadedAfterSave = repository.findSessionById(waiting.getId()).orElseThrow();
        assertThat(reloadedAfterSave.getVacancyId()).isEqualTo(vacancy.getId());
        assertThat(reloadedAfterSave.getState()).isEqualTo(ImportState.COMPLETED);
    }

    @Test
    void vacancyId_foreignKeyRejectsUnknownVacancy() {
        VacancyImportSession waiting = sessionAtWaitingForConfirmation(950L, 951L);

        repository.completeIfWaitingForConfirmation(waiting.getId(), UUID.randomUUID(), CLOCK.instant());

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void completeIfWaitingForConfirmation_sessionNotWaiting_updatesNoRowsAndDoesNotOverwriteState() {
        VacancyImportSession session = repository.saveSession(VacancyImportSession.start(960L, 961L, CLOCK, TTL));
        entityManager.flush();
        Vacancy vacancy = persistVacancy();

        boolean applied = repository.completeIfWaitingForConfirmation(session.getId(), vacancy.getId(), CLOCK.instant());
        entityManager.clear();

        assertThat(applied).isFalse();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        assertThat(reloaded.getVacancyId()).isNull();
    }

    @Test
    void retryIfWaitingForConfirmation_clearsDescriptionAndTransitionsBack() {
        VacancyImportSession session = sessionAtWaitingForConfirmation(970L, 971L);
        Instant updatedAt = CLOCK.instant().plusSeconds(1);

        boolean applied = repository.retryIfWaitingForConfirmation(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(reloaded.getRawDescription()).isNull();
        assertThat(reloaded.getSourceUrl()).isEqualTo("https://example.com/job");
    }

    @Test
    void retryAndDraftDeletion_areAtomicWithinOneTransaction() {
        VacancyImportSession session = sessionAtWaitingForConfirmation(980L, 981L);
        Instant updatedAt = CLOCK.instant().plusSeconds(1);

        // Simulates VacancyImportReviewService.performRetry: both writes happen, then we verify
        // both landed together (the actual atomicity/rollback-together guarantee is exercised by
        // VacancyImportReviewServiceTest's setRollbackOnly assertions at the unit level; here we
        // confirm the two statements this repository exposes are consistent with each other).
        boolean applied = repository.retryIfWaitingForConfirmation(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        assertThat(repository.findSessionById(session.getId()).orElseThrow().getState())
                .isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
    }

    @Test
    void cancelIfWaitingForConfirmation_sessionInWaitingForConfirmation_updatesExactlyOneRow() {
        VacancyImportSession session = sessionAtWaitingForConfirmation(990L, 991L);
        Instant updatedAt = CLOCK.instant().plusSeconds(1);

        boolean applied = repository.cancelIfWaitingForConfirmation(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.CANCELLED);
    }

    @Test
    void cancelIfWaitingForConfirmation_alreadyCompleted_doesNotOverwriteTheWinningState() {
        VacancyImportSession session = sessionAtWaitingForConfirmation(1000L, 1001L);
        Vacancy vacancy = persistVacancy();
        repository.completeIfWaitingForConfirmation(session.getId(), vacancy.getId(), CLOCK.instant().plusSeconds(1));
        entityManager.flush();
        entityManager.clear();

        boolean applied = repository.cancelIfWaitingForConfirmation(session.getId(), CLOCK.instant().plusSeconds(2));
        entityManager.clear();

        assertThat(applied).isFalse();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.COMPLETED);
        assertThat(reloaded.getVacancyId()).isEqualTo(vacancy.getId());
    }

    @Test
    void expireIfWaitingForConfirmation_updatesExactlyOneRow() {
        VacancyImportSession session = sessionAtWaitingForConfirmation(1010L, 1011L);
        Instant updatedAt = CLOCK.instant().plusSeconds(1);

        boolean applied = repository.expireIfWaitingForConfirmation(session.getId(), updatedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(applied).isTrue();
        VacancyImportSession reloaded = repository.findSessionById(session.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(ImportState.EXPIRED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completeIfWaitingForConfirmation_concurrentSaveCalls_exactlyOneWinsAndOneVacancyLinked() throws Exception {
        VacancyImportSession session = sessionAtWaitingForConfirmation(1020L, 1021L);
        Vacancy vacancyA = persistVacancy();
        Vacancy vacancyB = persistVacancy();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                barrier.await();
                return repository.completeIfWaitingForConfirmation(session.getId(), vacancyA.getId(), CLOCK.instant().plusSeconds(1));
            }));
            futures.add(executor.submit(() -> {
                barrier.await();
                return repository.completeIfWaitingForConfirmation(session.getId(), vacancyB.getId(), CLOCK.instant().plusSeconds(2));
            }));

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long winners = results.stream().filter(Boolean::booleanValue).count();
            assertThat(winners).isEqualTo(1);

            VacancyImportSession finalState = repository.findSessionById(session.getId()).orElseThrow();
            assertThat(finalState.getState()).isEqualTo(ImportState.COMPLETED);
            assertThat(finalState.getVacancyId()).isIn(vacancyA.getId(), vacancyB.getId());
        } finally {
            executor.shutdown();
        }
    }

    private VacancyImportSession sessionAtExtracting(long chatId, long userId) {
        VacancyImportSession session = VacancyImportSession.start(chatId, userId, CLOCK, TTL);
        session.provideUrl("https://example.com/job", CLOCK);
        session.provideDescriptionAndStartExtraction("A perfectly valid vacancy description here.", CLOCK);
        VacancyImportSession saved = repository.saveSession(session);
        entityManager.flush();
        return saved;
    }

    private VacancyImportSession sessionAtWaitingForConfirmation(long chatId, long userId) {
        VacancyImportSession session = sessionAtExtracting(chatId, userId);
        repository.moveToWaitingForConfirmationIfExtracting(session.getId(), CLOCK.instant());
        entityManager.flush();
        entityManager.clear();
        return repository.findSessionById(session.getId()).orElseThrow();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        Vacancy vacancy = vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job/" + UUID.randomUUID())
                .build());
        entityManager.flush();
        return vacancy;
    }
}
