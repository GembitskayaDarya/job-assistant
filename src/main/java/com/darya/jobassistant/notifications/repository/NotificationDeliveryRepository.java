package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.dto.NotificationDelivery;
import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.dto.NotificationReservationResult;
import com.darya.jobassistant.notifications.entity.NotificationDeliveryEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Application-facing delivery persistence contract. Callers should only rely on
 * {@link #reserve}, {@link #markSent} and {@link #markFailed} - these are the only methods
 * whose signatures are part of the intended application contract, and they exclusively use
 * domain types ({@link NotificationDelivery} and friends), never the JPA entity or a Spring
 * exception. The {@code JpaRepository} superinterface is an implementation detail that lets
 * this interface stay the single port + adapter, per this project's repository convention.
 */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    /**
     * Atomically reserves a PENDING delivery for the given vacancy/recipient pair, using the
     * unique constraint on (vacancy_id, recipient_chat_id) as the sole source of truth for
     * novelty. Avoids an exists()-then-save() check-then-act race across concurrent callers.
     */
    default NotificationReservationResult reserve(UUID vacancyId, Long recipientChatId, Instant createdAt) {
        return insertReservationIfAbsent(vacancyId, recipientChatId, createdAt)
                .map(entity -> NotificationReservationResult.reserved(toDomain(entity)))
                .orElseGet(NotificationReservationResult::alreadyExists);
    }

    /**
     * Atomically transitions PENDING -> SENT. The current status is part of the update's
     * predicate, so a delivery that is already SENT/FAILED is left untouched.
     */
    default NotificationDeliveryTransitionResult markSent(UUID deliveryId, Instant sentAt) {
        return toTransitionResult(deliveryId, markSentIfPending(deliveryId, sentAt));
    }

    /**
     * Atomically transitions PENDING -> FAILED. The current status is part of the update's
     * predicate, so a delivery that is already SENT/FAILED is left untouched.
     */
    default NotificationDeliveryTransitionResult markFailed(UUID deliveryId, Instant failedAt, String failureCode) {
        return toTransitionResult(deliveryId, markFailedIfPending(deliveryId, failedAt, failureCode));
    }

    private NotificationDeliveryTransitionResult toTransitionResult(
            UUID deliveryId, Optional<NotificationDeliveryEntity> updated) {
        if (updated.isPresent()) {
            return NotificationDeliveryTransitionResult.updated(toDomain(updated.get()));
        }
        // The conditional UPDATE already affected zero rows atomically; this lookup is only
        // a best-effort diagnostic to tell "no such delivery" apart from "wrong current state" -
        // it never re-decides whether to write.
        return findById(deliveryId).isPresent()
                ? NotificationDeliveryTransitionResult.invalidState()
                : NotificationDeliveryTransitionResult.notFound();
    }

    private static NotificationDelivery toDomain(NotificationDeliveryEntity entity) {
        return new NotificationDelivery(
                entity.getId(),
                entity.getVacancyId(),
                entity.getRecipientChatId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getFailedAt(),
                entity.getFailureCode());
    }

    /**
     * Low-level primitive backing {@link #reserve}. Relies on PostgreSQL's
     * {@code ON CONFLICT ... DO NOTHING} to resolve duplicates entirely inside the database
     * engine, so a duplicate never raises a unique-constraint exception here.
     */
    @Query(value = """
            INSERT INTO notification_delivery (vacancy_id, recipient_chat_id, status, created_at, updated_at)
            VALUES (:vacancyId, :recipientChatId, 'PENDING', :createdAt, :createdAt)
            ON CONFLICT (vacancy_id, recipient_chat_id)
            DO NOTHING
            RETURNING *
            """, nativeQuery = true)
    Optional<NotificationDeliveryEntity> insertReservationIfAbsent(
            @Param("vacancyId") UUID vacancyId,
            @Param("recipientChatId") Long recipientChatId,
            @Param("createdAt") Instant createdAt);

    /**
     * Low-level primitive backing {@link #markSent}. The {@code WHERE status = 'PENDING'}
     * predicate makes the PENDING -> SENT transition atomic and monotonic: a row already SENT
     * or FAILED never matches, so it is never overwritten.
     */
    @Query(value = """
            UPDATE notification_delivery
            SET status = 'SENT', sent_at = :sentAt, updated_at = :sentAt
            WHERE id = :deliveryId AND status = 'PENDING'
            RETURNING *
            """, nativeQuery = true)
    Optional<NotificationDeliveryEntity> markSentIfPending(
            @Param("deliveryId") UUID deliveryId,
            @Param("sentAt") Instant sentAt);

    /**
     * Low-level primitive backing {@link #markFailed}. The {@code WHERE status = 'PENDING'}
     * predicate makes the PENDING -> FAILED transition atomic and monotonic: a row already SENT
     * or FAILED never matches, so it is never overwritten.
     */
    @Query(value = """
            UPDATE notification_delivery
            SET status = 'FAILED', failed_at = :failedAt, failure_code = :failureCode, updated_at = :failedAt
            WHERE id = :deliveryId AND status = 'PENDING'
            RETURNING *
            """, nativeQuery = true)
    Optional<NotificationDeliveryEntity> markFailedIfPending(
            @Param("deliveryId") UUID deliveryId,
            @Param("failedAt") Instant failedAt,
            @Param("failureCode") String failureCode);
}
