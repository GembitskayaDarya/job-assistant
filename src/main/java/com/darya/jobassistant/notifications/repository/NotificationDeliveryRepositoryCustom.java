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
}
