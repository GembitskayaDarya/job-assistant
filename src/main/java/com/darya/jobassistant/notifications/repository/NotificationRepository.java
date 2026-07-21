package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.entity.Notification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
