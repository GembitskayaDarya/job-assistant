package com.darya.jobassistant.notifications.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    private final UUID id = UUID.randomUUID();
    private final UUID vacancyId = UUID.randomUUID();
    private final Long recipientChatId = 12345L;
    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void validPendingDelivery_isAccepted() {
        NotificationDelivery delivery = new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.PENDING, createdAt, null, null, null);

        assertThat(delivery.status()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(delivery.sentAt()).isNull();
        assertThat(delivery.failedAt()).isNull();
        assertThat(delivery.failureCode()).isNull();
    }

    @Test
    void validSentDelivery_isAccepted() {
        Instant sentAt = createdAt.plus(1, ChronoUnit.MINUTES);

        NotificationDelivery delivery = new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.SENT, createdAt, sentAt, null, null);

        assertThat(delivery.status()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(delivery.sentAt()).isEqualTo(sentAt);
    }

    @Test
    void validFailedDelivery_isAccepted() {
        Instant failedAt = createdAt.plus(1, ChronoUnit.MINUTES);

        NotificationDelivery delivery = new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, null, failedAt, "RATE_LIMITED");

        assertThat(delivery.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.failedAt()).isEqualTo(failedAt);
        assertThat(delivery.failureCode()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void validFailedDelivery_withoutFailureCode_isAccepted() {
        Instant failedAt = createdAt.plus(1, ChronoUnit.MINUTES);

        NotificationDelivery delivery = new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, null, failedAt, null);

        assertThat(delivery.failureCode()).isNull();
    }

    @Test
    void rejectsNullId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                null, vacancyId, recipientChatId, NotificationDeliveryStatus.PENDING, createdAt, null, null, null));
    }

    @Test
    void rejectsNullVacancyId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, null, recipientChatId, NotificationDeliveryStatus.PENDING, createdAt, null, null, null));
    }

    @Test
    void rejectsNullRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, null, NotificationDeliveryStatus.PENDING, createdAt, null, null, null));
    }

    @Test
    void rejectsZeroRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, 0L, NotificationDeliveryStatus.PENDING, createdAt, null, null, null));
    }

    @Test
    void rejectsNullStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, null, createdAt, null, null, null));
    }

    @Test
    void rejectsNullCreatedAt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.PENDING, null, null, null, null));
    }

    @Test
    void rejectsBlankFailureCode() {
        Instant failedAt = createdAt.plus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, null, failedAt, "   "));
    }

    @Test
    void rejectsPendingWithSentAt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.PENDING, createdAt,
                createdAt.plus(1, ChronoUnit.MINUTES), null, null));
    }

    @Test
    void rejectsPendingWithFailedAt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.PENDING, createdAt,
                null, createdAt.plus(1, ChronoUnit.MINUTES), null));
    }

    @Test
    void rejectsSentWithoutSentAt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.SENT, createdAt, null, null, null));
    }

    @Test
    void rejectsSentWithFailedAt() {
        Instant sentAt = createdAt.plus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.SENT, createdAt, sentAt, sentAt, null));
    }

    @Test
    void rejectsSentWithFailureCode() {
        Instant sentAt = createdAt.plus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.SENT, createdAt, sentAt, null, "SOME_CODE"));
    }

    @Test
    void rejectsFailedWithoutFailedAt() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, null, null, null));
    }

    @Test
    void rejectsFailedWithSentAt() {
        Instant failedAt = createdAt.plus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, failedAt, failedAt, null));
    }

    @Test
    void rejectsSentAtEarlierThanCreatedAt() {
        Instant earlierSentAt = createdAt.minus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.SENT, createdAt, earlierSentAt, null, null));
    }

    @Test
    void rejectsFailedAtEarlierThanCreatedAt() {
        Instant earlierFailedAt = createdAt.minus(1, ChronoUnit.MINUTES);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDelivery(
                id, vacancyId, recipientChatId, NotificationDeliveryStatus.FAILED, createdAt, null, earlierFailedAt, null));
    }
}
