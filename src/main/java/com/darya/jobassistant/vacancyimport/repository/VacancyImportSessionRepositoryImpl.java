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
}
