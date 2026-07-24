package com.darya.jobassistant.vacancyimport.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;

/**
 * Backs {@link VacancyImportSessionRepositoryCustom}. Uses a plain conditional
 * {@code executeUpdate()}, same as {@code NotificationDeliveryRepositoryImpl} - the row-count it
 * returns is the only signal the caller needs (won or lost the race), so there is no need to
 * load or refresh a managed entity the way that class does for its richer transition result.
 */
public class VacancyImportSessionRepositoryImpl implements VacancyImportSessionRepositoryCustom {

    private static final String ACCEPT_DESCRIPTION_SQL = """
            UPDATE vacancy_import_session
            SET state = 'EXTRACTING', raw_description = :rawDescription, updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'WAITING_FOR_DESCRIPTION'
            """;

    private static final String MOVE_TO_WAITING_FOR_CONFIRMATION_SQL = """
            UPDATE vacancy_import_session
            SET state = 'WAITING_FOR_CONFIRMATION', updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'EXTRACTING'
            """;

    private static final String MOVE_TO_FAILED_SQL = """
            UPDATE vacancy_import_session
            SET state = 'FAILED', updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'EXTRACTING'
            """;

    private static final String COMPLETE_SQL = """
            UPDATE vacancy_import_session
            SET state = 'COMPLETED', vacancy_id = :vacancyId, updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'WAITING_FOR_CONFIRMATION'
            """;

    private static final String RETRY_SQL = """
            UPDATE vacancy_import_session
            SET state = 'WAITING_FOR_DESCRIPTION', raw_description = NULL, updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'WAITING_FOR_CONFIRMATION'
            """;

    private static final String CANCEL_SQL = """
            UPDATE vacancy_import_session
            SET state = 'CANCELLED', updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'WAITING_FOR_CONFIRMATION'
            """;

    private static final String EXPIRE_SQL = """
            UPDATE vacancy_import_session
            SET state = 'EXPIRED', updated_at = :updatedAt
            WHERE id = :sessionId AND state = 'WAITING_FOR_CONFIRMATION'
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean acceptDescriptionIfWaiting(UUID sessionId, String rawDescription, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(ACCEPT_DESCRIPTION_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("rawDescription", rawDescription)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean moveToWaitingForConfirmationIfExtracting(UUID sessionId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(MOVE_TO_WAITING_FOR_CONFIRMATION_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean moveToFailedIfExtracting(UUID sessionId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(MOVE_TO_FAILED_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean completeIfWaitingForConfirmation(UUID sessionId, UUID vacancyId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(COMPLETE_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("vacancyId", vacancyId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean retryIfWaitingForConfirmation(UUID sessionId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(RETRY_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean cancelIfWaitingForConfirmation(UUID sessionId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(CANCEL_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }

    @Override
    public boolean expireIfWaitingForConfirmation(UUID sessionId, Instant updatedAt) {
        int updatedRows = entityManager.createNativeQuery(EXPIRE_SQL)
                .setParameter("sessionId", sessionId)
                .setParameter("updatedAt", updatedAt)
                .executeUpdate();
        return updatedRows == 1;
    }
}
