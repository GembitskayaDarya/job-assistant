package com.darya.jobassistant.notifications.repository;

import com.darya.jobassistant.notifications.dto.NotificationDelivery;
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
 * {@link #reserve}, {@link #markSent} and {@link #markFailed} (the latter two backed by
 * {@link NotificationDeliveryRepositoryCustom} / {@link NotificationDeliveryRepositoryImpl}) -
 * these are the only methods whose signatures are part of the intended application contract,
 * and they exclusively use domain types ({@link NotificationDelivery} and friends), never the
 * JPA entity or a Spring exception. The {@code JpaRepository} superinterface is an
 * implementation detail that lets this interface stay the single port + adapter, per this
 * project's repository convention.
 */
public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDeliveryEntity, UUID>, NotificationDeliveryRepositoryCustom {

    /**
     * Plain lookup backing {@code VacancyRecommendationProcessingService}'s retry path: when
     * {@link #reserve} reports {@code ALREADY_EXISTS}, this loads the existing row so the caller
     * can decide what "already exists" means for it (already {@code SENT}, still {@code PENDING}
     * from a previous attempt that crashed before resolving, or {@code FAILED} and eligible for
     * {@link NotificationDeliveryRepositoryCustom#retryFailed}).
     */
    default Optional<NotificationDelivery> findExistingDelivery(UUID vacancyId, Long recipientChatId) {
        return findByVacancyIdAndRecipientChatId(vacancyId, recipientChatId).map(NotificationDeliveryRepository::toDomain);
    }

    Optional<NotificationDeliveryEntity> findByVacancyIdAndRecipientChatId(UUID vacancyId, Long recipientChatId);

    /**
     * Atomically reserves a PENDING delivery for the given vacancy/recipient pair, using the
     * unique constraint on (vacancy_id, recipient_chat_id) as the sole source of truth for
     * novelty. Avoids an exists()-then-save() check-then-act race across concurrent callers.
     * Safe to map the row straight to the entity class: a freshly generated UUID can never
     * already be managed in the current persistence context, so there is no stale-instance risk
     * (unlike {@link #markSent}/{@link #markFailed}, which transition an existing row).
     */
    default NotificationReservationResult reserve(UUID vacancyId, Long recipientChatId, Instant createdAt) {
        return insertReservationIfAbsent(vacancyId, recipientChatId, createdAt)
                .map(entity -> NotificationReservationResult.reserved(toDomain(entity)))
                .orElseGet(NotificationReservationResult::alreadyExists);
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

    static NotificationDelivery toDomain(NotificationDeliveryEntity entity) {
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
}
