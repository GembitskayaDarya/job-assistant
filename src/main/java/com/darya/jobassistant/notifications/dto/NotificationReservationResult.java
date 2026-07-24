package com.darya.jobassistant.notifications.dto;

import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;

public record NotificationReservationResult(Status status, NotificationDelivery delivery) {

    public enum Status {
        RESERVED,
        ALREADY_EXISTS
    }

    public NotificationReservationResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.RESERVED) {
            if (delivery == null) {
                throw new IllegalArgumentException("a RESERVED result must expose the newly created delivery");
            }
            if (delivery.status() != NotificationDeliveryStatus.PENDING) {
                throw new IllegalArgumentException("a RESERVED result must expose a PENDING delivery");
            }
        }
        if (status == Status.ALREADY_EXISTS && delivery != null) {
            throw new IllegalArgumentException("an ALREADY_EXISTS result must not expose a delivery");
        }
    }

    public static NotificationReservationResult reserved(NotificationDelivery delivery) {
        if (delivery == null) {
            throw new IllegalArgumentException("delivery must not be null");
        }
        return new NotificationReservationResult(Status.RESERVED, delivery);
    }

    public static NotificationReservationResult alreadyExists() {
        return new NotificationReservationResult(Status.ALREADY_EXISTS, null);
    }

    public boolean isReserved() {
        return status == Status.RESERVED;
    }
}
