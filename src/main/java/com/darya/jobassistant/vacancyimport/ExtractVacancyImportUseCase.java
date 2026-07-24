package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.ExtractVacancyImportResult;
import java.util.UUID;

/**
 * Runs AI extraction for a session's already-persisted raw description, turning it into a
 * {@code VacancyImportDraft} and advancing the session from {@code EXTRACTING} to
 * {@code WAITING_FOR_CONFIRMATION}. Idempotent: calling it again for a session that already has a
 * draft returns that draft without a new AI call.
 */
public interface ExtractVacancyImportUseCase {

    ExtractVacancyImportResult extract(UUID sessionId);
}
