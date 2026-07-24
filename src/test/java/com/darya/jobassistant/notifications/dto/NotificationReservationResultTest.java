package com.darya.jobassistant.notifications.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationReservationResultTest {

    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reserved_exposesThePendingDelivery() {
        NotificationDelivery pending = pendingDelivery();

        NotificationReservationResult result = NotificationReservationResult.reserved(pending);

        assertThat(result.status()).isEqualTo(NotificationReservationResult.Status.RESERVED);
        assertThat(result.delivery()).isSameAs(pending);
        assertThat(result.isReserved()).isTrue();
    }

    @Test
    void reserved_requiresNonNullDelivery() {
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationReservationResult.reserved(null));
    }

    @Test
    void reserved_rejectsANonPendingDelivery() {
        NotificationDelivery sent = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), 111L, NotificationDeliveryStatus.SENT,
                createdAt, createdAt, null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotificationReservationResult(NotificationReservationResult.Status.RESERVED, sent));
    }

    @Test
    void alreadyExists_doesNotExposeADelivery() {
        NotificationReservationResult result = NotificationReservationResult.alreadyExists();

        assertThat(result.status()).isEqualTo(NotificationReservationResult.Status.ALREADY_EXISTS);
        assertThat(result.delivery()).isNull();
        assertThat(result.isReserved()).isFalse();
    }

    @Test
    void rejectsNullStatus() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NotificationReservationResult(null, pendingDelivery()));
    }

    @Test
    void rejectsAlreadyExistsStatusWithNonNullDelivery() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new NotificationReservationResult(NotificationReservationResult.Status.ALREADY_EXISTS, pendingDelivery()));
    }

    private NotificationDelivery pendingDelivery() {
        return new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), 111L, NotificationDeliveryStatus.PENDING,
                createdAt, null, null, null);
    }
}
