package com.darya.jobassistant.notifications.dto;

public record NotificationDeliveryTransitionResult(Status status, NotificationDelivery delivery) {

    public enum Status {
        UPDATED,
        NOT_FOUND,
        INVALID_STATE
    }

    public NotificationDeliveryTransitionResult {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.UPDATED && delivery == null) {
            throw new IllegalArgumentException("an UPDATED result must expose the transitioned delivery");
        }
        if (status != Status.UPDATED && delivery != null) {
            throw new IllegalArgumentException(status + " result must not expose a delivery");
        }
    }

    public static NotificationDeliveryTransitionResult updated(NotificationDelivery delivery) {
        if (delivery == null) {
            throw new IllegalArgumentException("delivery must not be null");
        }
        return new NotificationDeliveryTransitionResult(Status.UPDATED, delivery);
    }

    public static NotificationDeliveryTransitionResult notFound() {
        return new NotificationDeliveryTransitionResult(Status.NOT_FOUND, null);
    }

    public static NotificationDeliveryTransitionResult invalidState() {
        return new NotificationDeliveryTransitionResult(Status.INVALID_STATE, null);
    }

    public boolean isUpdated() {
        return status == Status.UPDATED;
    }
}
