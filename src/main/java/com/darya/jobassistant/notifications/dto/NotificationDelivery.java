package com.darya.jobassistant.notifications.dto;

import com.darya.jobassistant.notifications.entity.NotificationDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationDelivery(
        UUID id,
        UUID vacancyId,
        Long recipientChatId,
        NotificationDeliveryStatus status,
        Instant createdAt,
        Instant sentAt,
        Instant failedAt,
        String failureCode
) {
    public NotificationDelivery {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
        if (recipientChatId == null || recipientChatId == 0L) {
            throw new IllegalArgumentException("recipientChatId must be a valid Telegram chat ID");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (failureCode != null && failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode must not be blank when present");
        }

        switch (status) {
            case PENDING -> {
                if (sentAt != null || failedAt != null) {
                    throw new IllegalArgumentException("a PENDING delivery must not have sentAt or failedAt");
                }
                if (failureCode != null) {
                    throw new IllegalArgumentException("a PENDING delivery must not have a failureCode");
                }
            }
            case SENT -> {
                if (sentAt == null) {
                    throw new IllegalArgumentException("a SENT delivery must have a sentAt timestamp");
                }
                if (failedAt != null || failureCode != null) {
                    throw new IllegalArgumentException("a SENT delivery must not have failedAt or a failureCode");
                }
            }
            case FAILED -> {
                if (failedAt == null) {
                    throw new IllegalArgumentException("a FAILED delivery must have a failedAt timestamp");
                }
                if (sentAt != null) {
                    throw new IllegalArgumentException("a FAILED delivery must not have a sentAt timestamp");
                }
            }
        }

        if (sentAt != null && sentAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("sentAt must not be earlier than createdAt");
        }
        if (failedAt != null && failedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("failedAt must not be earlier than createdAt");
        }
    }
}
