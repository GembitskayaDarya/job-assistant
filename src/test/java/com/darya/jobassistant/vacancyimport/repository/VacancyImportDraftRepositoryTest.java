package com.darya.jobassistant.vacancyimport.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class VacancyImportDraftRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(30);

    @Autowired
    private VacancyImportDraftRepository draftRepository;

    @Autowired
    private VacancyImportSessionRepository sessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void saveDraft_thenFindDraftBySessionId_returnsEquivalentDraft() {
        UUID sessionId = persistSession(1L, 2L);
        Instant now = CLOCK.instant();
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Kafka"),
                "10-15k PLN");

        VacancyImportDraft saved = draftRepository.saveDraft(sessionId, data, now);
        entityManager.flush();
        entityManager.clear();

        Optional<VacancyImportDraft> loaded = draftRepository.findDraftBySessionId(sessionId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo(saved.id());
        assertThat(loaded.get().sessionId()).isEqualTo(sessionId);
    }

    @Test
    void draftFields_surviveRoundTripIncludingUnicodeAndLists() {
        UUID sessionId = persistSession(10L, 20L);
        Instant now = CLOCK.instant();
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Starszy Inżynier Java 日本語 Разработчик",
                "Acme Sp. z o.o.",
                "Warszawa / Zdalnie",
                RemotePolicy.HYBRID,
                List.of("B2B", "UoP"),
                List.of("Java", "Kafka", "PostgreSQL", "Kubernetes"),
                "20 000 – 28 000 PLN netto");

        draftRepository.saveDraft(sessionId, data, now);
        entityManager.flush();
        entityManager.clear();

        VacancyImportDraft loaded = draftRepository.findDraftBySessionId(sessionId).orElseThrow();
        assertThat(loaded.data()).isEqualTo(data);
        assertThat(loaded.data().contractTypes()).containsExactly("B2B", "UoP");
        assertThat(loaded.data().requiredSkills()).containsExactly("Java", "Kafka", "PostgreSQL", "Kubernetes");
        assertThat(loaded.createdAt()).isEqualTo(now);
        assertThat(loaded.updatedAt()).isEqualTo(now);
    }

    @Test
    void savingSecondDraftForSameSession_violatesUniqueConstraint() {
        UUID sessionId = persistSession(30L, 40L);
        ExtractedVacancyData data = minimalData();
        draftRepository.saveDraft(sessionId, data, CLOCK.instant());
        entityManager.flush();

        draftRepository.saveDraft(sessionId, data, CLOCK.instant());

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void savingDraft_forUnknownSession_violatesForeignKeyConstraint() {
        draftRepository.saveDraft(UUID.randomUUID(), minimalData(), CLOCK.instant());

        assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deletingSession_cascadesToItsDraft() {
        UUID sessionId = persistSession(50L, 60L);
        draftRepository.saveDraft(sessionId, minimalData(), CLOCK.instant());
        entityManager.flush();
        assertThat(draftRepository.findDraftBySessionId(sessionId)).isPresent();

        entityManager
                .createNativeQuery("DELETE FROM vacancy_import_session WHERE id = ?1")
                .setParameter(1, sessionId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(draftRepository.findDraftBySessionId(sessionId)).isEmpty();
    }

    private UUID persistSession(long chatId, long userId) {
        VacancyImportSession session = VacancyImportSession.start(chatId, userId, CLOCK, TTL);
        VacancyImportSession saved = sessionRepository.saveSession(session);
        entityManager.flush();
        return saved.getId();
    }

    private ExtractedVacancyData minimalData() {
        return new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);
    }
}
