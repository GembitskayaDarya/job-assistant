package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import java.time.Instant;
import java.util.UUID;

public interface NotificationDeliveryRepositoryCustom {

    /**
     * Atomically transitions PENDING -> SENT. The current status is part of the update's
     * predicate, so a delivery that is already SENT/FAILED is left untouched.
     */
    NotificationDeliveryTransitionResult markSent(UUID deliveryId, Instant sentAt);

    /**
     * Atomically transitions PENDING -> FAILED. The current status is part of the update's
     * predicate, so a delivery that is already SENT/FAILED is left untouched.
     */
    NotificationDeliveryTransitionResult markFailed(UUID deliveryId, Instant failedAt, String failureCode);

    /**
     * Atomically transitions FAILED -> PENDING, clearing {@code failedAt}/{@code failureCode} (a
     * {@code PENDING} delivery must not carry either - see {@code NotificationDelivery}'s own
     * constructor). The current status is part of the update's predicate, so a delivery that is
     * already {@code SENT} or still {@code PENDING} is left untouched. Used only by {@code
     * VacancyRecommendationProcessingService} to retry a previously failed send on the *same*
     * delivery row, reusing this aggregate's existing state machine rather than a second delivery
     * table or a new reservation (which {@link
     * NotificationDeliveryRepository#reserve(java.util.UUID, Long, Instant)}'s unique constraint
     * on {@code (vacancy_id, recipient_chat_id)} would refuse for an already-existing row anyway).
     */
    NotificationDeliveryTransitionResult retryFailed(UUID deliveryId, Instant retriedAt);
}
