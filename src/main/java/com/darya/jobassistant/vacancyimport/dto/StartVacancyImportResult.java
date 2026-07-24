package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.UUID;

/**
 * Provider-independent outcome of {@code StartVacancyImportUseCase}. {@code AlreadyActive} only
 * ever carries an active {@link ImportState} (WAITING_FOR_URL, WAITING_FOR_DESCRIPTION,
 * EXTRACTING or WAITING_FOR_CONFIRMATION) - the Telegram adapter can switch on it exhaustively
 * without needing to special-case terminal states.
 */
public sealed interface StartVacancyImportResult {

    record Started(UUID sessionId) implements StartVacancyImportResult {
    }

    record AlreadyActive(UUID sessionId, ImportState state) implements StartVacancyImportResult {
    }
}
