package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.entity.Notification;
import com.darya.jobassistant.notifications.entity.NotificationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTelegramUserId(UUID telegramUserId);

    List<Notification> findByStatus(NotificationStatus status);
}
