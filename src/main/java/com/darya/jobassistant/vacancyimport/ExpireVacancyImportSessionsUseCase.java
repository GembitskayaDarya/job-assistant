package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.ExpireVacancyImportSessionsResult;

/**
 * Expires one bounded batch of abandoned active import sessions. Triggered by {@code
 * VacancyImportExpirationJob} on a schedule, but the use case itself has no Spring scheduling
 * dependency - it is a plain, independently callable batch operation.
 */
public interface ExpireVacancyImportSessionsUseCase {

    ExpireVacancyImportSessionsResult expireBatch();
}
