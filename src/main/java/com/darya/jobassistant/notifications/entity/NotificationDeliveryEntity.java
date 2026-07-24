package com.darya.jobassistant.notifications.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "notification_delivery")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class NotificationDeliveryEntity extends BaseEntity {

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "recipient_chat_id", nullable = false)
    private Long recipientChatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeliveryStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;
}
