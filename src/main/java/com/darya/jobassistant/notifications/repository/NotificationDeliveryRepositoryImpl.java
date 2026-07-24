package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.entity.NotificationDeliveryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;

/**
 * Backs {@link NotificationDeliveryRepositoryCustom}. Uses a plain conditional
 * {@code executeUpdate()} - not a {@code RETURNING}-mapped-to-entity query - followed by an
 * explicit {@link EntityManager#refresh}. {@link NotificationDeliveryRepository#reserve} may
 * already have loaded a {@code NotificationDeliveryEntity} with this same id into the current
 * persistence context; Hibernate's identity map would otherwise hand back that stale,
 * pre-transition managed instance instead of the row's new column values, since native queries
 * mapped to an entity class never overwrite an already-managed instance's fields.
 */
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepositoryCustom {

    private static final String MARK_SENT_SQL = """
            UPDATE notification_delivery
            SET status = 'SENT', sent_at = :sentAt, updated_at = :sentAt
            WHERE id = :deliveryId AND status = 'PENDING'
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE notification_delivery
            SET status = 'FAILED', failed_at = :failedAt, failure_code = :failureCode, updated_at = :failedAt
            WHERE id = :deliveryId AND status = 'PENDING'
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public NotificationDeliveryTransitionResult markSent(UUID deliveryId, Instant sentAt) {
        int updatedRows = entityManager.createNativeQuery(MARK_SENT_SQL)
                .setParameter("deliveryId", deliveryId)
                .setParameter("sentAt", sentAt)
                .executeUpdate();
        return toTransitionResult(deliveryId, updatedRows);
    }

    @Override
    public NotificationDeliveryTransitionResult markFailed(UUID deliveryId, Instant failedAt, String failureCode) {
        int updatedRows = entityManager.createNativeQuery(MARK_FAILED_SQL)
                .setParameter("deliveryId", deliveryId)
                .setParameter("failedAt", failedAt)
                .setParameter("failureCode", failureCode)
                .executeUpdate();
        return toTransitionResult(deliveryId, updatedRows);
    }

    private NotificationDeliveryTransitionResult toTransitionResult(UUID deliveryId, int updatedRows) {
        NotificationDeliveryEntity current = entityManager.find(NotificationDeliveryEntity.class, deliveryId);
        if (updatedRows == 0) {
            return current != null
                    ? NotificationDeliveryTransitionResult.invalidState()
                    : NotificationDeliveryTransitionResult.notFound();
        }
        entityManager.refresh(current);
        return NotificationDeliveryTransitionResult.updated(NotificationDeliveryRepository.toDomain(current));
    }
}
