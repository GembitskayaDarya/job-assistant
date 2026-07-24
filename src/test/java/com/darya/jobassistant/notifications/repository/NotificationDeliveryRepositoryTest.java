package com.darya.jobassistant.notifications.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.dto.NotificationReservationResult;
import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class NotificationDeliveryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void reserve_newVacancyAndRecipient_returnsReservedWithDurableUuidAndPendingStatus() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();

        NotificationReservationResult result = notificationDeliveryRepository.reserve(vacancyId, 111L, createdAt);

        assertThat(result.isReserved()).isTrue();
        assertThat(result.delivery().id()).isNotNull();
        assertThat(result.delivery().status()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(result.delivery().vacancyId()).isEqualTo(vacancyId);
        assertThat(result.delivery().recipientChatId()).isEqualTo(111L);
    }

    @Test
    void reserve_sameVacancyAndRecipientTwice_secondCallReturnsAlreadyExists() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();

        NotificationReservationResult first = notificationDeliveryRepository.reserve(vacancyId, 222L, createdAt);
        NotificationReservationResult second = notificationDeliveryRepository.reserve(vacancyId, 222L, createdAt);

        assertThat(first.isReserved()).isTrue();
        assertThat(second.isReserved()).isFalse();
        assertThat(second.delivery()).isNull();
        assertThat(countForVacancyAndRecipient(vacancyId, 222L)).isEqualTo(1);
    }

    @Test
    void reserve_sameVacancyDifferentRecipient_bothAreReserved() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();

        NotificationReservationResult first = notificationDeliveryRepository.reserve(vacancyId, 333L, createdAt);
        NotificationReservationResult second = notificationDeliveryRepository.reserve(vacancyId, 444L, createdAt);

        assertThat(first.isReserved()).isTrue();
        assertThat(second.isReserved()).isTrue();
    }

    @Test
    void reserve_differentVacanciesSameRecipient_bothAreReserved() {
        UUID vacancyOneId = newVacancy().getId();
        UUID vacancyTwoId = newVacancy().getId();
        Instant createdAt = Instant.now();

        NotificationReservationResult first = notificationDeliveryRepository.reserve(vacancyOneId, 555L, createdAt);
        NotificationReservationResult second = notificationDeliveryRepository.reserve(vacancyTwoId, 555L, createdAt);

        assertThat(first.isReserved()).isTrue();
        assertThat(second.isReserved()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reserve_concurrentReservationsForSameVacancyAndRecipient_exactlyOneReserved() throws Exception {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<NotificationReservationResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return notificationDeliveryRepository.reserve(vacancyId, 666L, createdAt);
                }));
            }

            List<NotificationReservationResult> results = new ArrayList<>();
            for (Future<NotificationReservationResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long reservedCount = results.stream().filter(NotificationReservationResult::isReserved).count();
            assertThat(reservedCount).isEqualTo(1);
            assertThat(countForVacancyAndRecipient(vacancyId, 666L)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void markSent_pendingDelivery_transitionsToSent() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancyId, 777L, createdAt).delivery().id();
        Instant sentAt = createdAt.plusSeconds(5);

        NotificationDeliveryTransitionResult result = notificationDeliveryRepository.markSent(deliveryId, sentAt);

        assertThat(result.isUpdated()).isTrue();
        assertThat(result.delivery().status()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(result.delivery().sentAt()).isEqualTo(sentAt);
    }

    @Test
    void markFailed_pendingDelivery_transitionsToFailed() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancyId, 888L, createdAt).delivery().id();
        Instant failedAt = createdAt.plusSeconds(5);

        NotificationDeliveryTransitionResult result = notificationDeliveryRepository.markFailed(deliveryId, failedAt, "RATE_LIMITED");

        assertThat(result.isUpdated()).isTrue();
        assertThat(result.delivery().status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.delivery().failedAt()).isEqualTo(failedAt);
        assertThat(result.delivery().failureCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void markFailed_alreadySentDelivery_isRejectedAndDoesNotOverwriteSentAt() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancyId, 999L, createdAt).delivery().id();
        Instant sentAt = createdAt.plusSeconds(5);
        notificationDeliveryRepository.markSent(deliveryId, sentAt);

        NotificationDeliveryTransitionResult result =
                notificationDeliveryRepository.markFailed(deliveryId, createdAt.plusSeconds(10), "TOO_LATE");

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.INVALID_STATE);
        assertThat(result.delivery()).isNull();
        var stored = notificationDeliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(stored.getSentAt()).isEqualTo(sentAt);
        assertThat(stored.getFailedAt()).isNull();
    }

    @Test
    void markSent_alreadyFailedDelivery_isRejectedAndDoesNotOverwriteFailedAt() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancyId, 1010L, createdAt).delivery().id();
        Instant failedAt = createdAt.plusSeconds(5);
        notificationDeliveryRepository.markFailed(deliveryId, failedAt, "RATE_LIMITED");

        NotificationDeliveryTransitionResult result =
                notificationDeliveryRepository.markSent(deliveryId, createdAt.plusSeconds(10));

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.INVALID_STATE);
        assertThat(result.delivery()).isNull();
        var stored = notificationDeliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(stored.getFailedAt()).isEqualTo(failedAt);
        assertThat(stored.getSentAt()).isNull();
    }

    @Test
    void markSent_repeatedCallOnAlreadySentDelivery_doesNotOverwriteTimestamp() {
        UUID vacancyId = newVacancy().getId();
        Instant createdAt = Instant.now();
        UUID deliveryId = notificationDeliveryRepository.reserve(vacancyId, 1111L, createdAt).delivery().id();
        Instant firstSentAt = createdAt.plusSeconds(5);
        notificationDeliveryRepository.markSent(deliveryId, firstSentAt);

        NotificationDeliveryTransitionResult result =
                notificationDeliveryRepository.markSent(deliveryId, createdAt.plusSeconds(50));

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.INVALID_STATE);
        var stored = notificationDeliveryRepository.findById(deliveryId).orElseThrow();
        assertThat(stored.getSentAt()).isEqualTo(firstSentAt);
    }

    @Test
    void markSent_unknownDeliveryId_returnsNotFound() {
        NotificationDeliveryTransitionResult result =
                notificationDeliveryRepository.markSent(UUID.randomUUID(), Instant.now());

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.NOT_FOUND);
        assertThat(result.delivery()).isNull();
    }

    @Test
    void markFailed_unknownDeliveryId_returnsNotFound() {
        NotificationDeliveryTransitionResult result =
                notificationDeliveryRepository.markFailed(UUID.randomUUID(), Instant.now(), "SOME_CODE");

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.NOT_FOUND);
        assertThat(result.delivery()).isNull();
    }

    private long countForVacancyAndRecipient(UUID vacancyId, Long recipientChatId) {
        return notificationDeliveryRepository.findAll().stream()
                .filter(d -> vacancyId.equals(d.getVacancyId()) && recipientChatId.equals(d.getRecipientChatId()))
                .count();
    }

    private Vacancy newVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job-" + UUID.randomUUID())
                .source("remoteok")
                .build());
    }
}
