package com.darya.jobassistant.notifications.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTransitionResultTest {

    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void updated_exposesTheTransitionedDelivery() {
        NotificationDelivery sent = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), 111L, NotificationDeliveryStatus.SENT,
                createdAt, createdAt, null, null);

        NotificationDeliveryTransitionResult result = NotificationDeliveryTransitionResult.updated(sent);

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.UPDATED);
        assertThat(result.delivery()).isSameAs(sent);
        assertThat(result.isUpdated()).isTrue();
    }

    @Test
    void updated_requiresNonNullDelivery() {
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationDeliveryTransitionResult.updated(null));
    }

    @Test
    void notFound_doesNotExposeADelivery() {
        NotificationDeliveryTransitionResult result = NotificationDeliveryTransitionResult.notFound();

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.NOT_FOUND);
        assertThat(result.delivery()).isNull();
        assertThat(result.isUpdated()).isFalse();
    }

    @Test
    void invalidState_doesNotExposeADelivery() {
        NotificationDeliveryTransitionResult result = NotificationDeliveryTransitionResult.invalidState();

        assertThat(result.status()).isEqualTo(NotificationDeliveryTransitionResult.Status.INVALID_STATE);
        assertThat(result.delivery()).isNull();
        assertThat(result.isUpdated()).isFalse();
    }

    @Test
    void rejectsNullStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDeliveryTransitionResult(null, null));
    }

    @Test
    void rejectsNotFoundStatusWithNonNullDelivery() {
        NotificationDelivery delivery = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), 111L, NotificationDeliveryStatus.PENDING,
                createdAt, null, null, null);

        assertThatIllegalArgumentException().isThrownBy(() ->
                new NotificationDeliveryTransitionResult(NotificationDeliveryTransitionResult.Status.NOT_FOUND, delivery));
    }

    @Test
    void rejectsInvalidStateStatusWithNonNullDelivery() {
        NotificationDelivery delivery = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), 111L, NotificationDeliveryStatus.PENDING,
                createdAt, null, null, null);

        assertThatIllegalArgumentException().isThrownBy(() -> new NotificationDeliveryTransitionResult(
                NotificationDeliveryTransitionResult.Status.INVALID_STATE, delivery));
    }
}
