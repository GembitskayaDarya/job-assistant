package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.util.UUID;

/**
 * Provider-independent outcome of {@link com.darya.jobassistant.vacancyimport.ExtractVacancyImportUseCase}.
 * {@code InvalidState} only ever carries a state other than {@code EXTRACTING} and
 * {@code WAITING_FOR_CONFIRMATION} - those two are handled by their own dedicated results instead.
 */
public sealed interface ExtractVacancyImportResult {

    /** A fresh AI call produced this draft and the session was moved on for the first time. */
    record Recognized(UUID sessionId, VacancyImportDraft draft) implements ExtractVacancyImportResult {
    }

    /** No new AI call was made: the session already had a draft (idempotent replay or a lost race). */
    record AlreadyRecognized(UUID sessionId, VacancyImportDraft draft) implements ExtractVacancyImportResult {
    }

    /** The AI provider failed or returned unusable data; the session was moved to {@code FAILED}. */
    record Failed(UUID sessionId) implements ExtractVacancyImportResult {
    }

    record InvalidState(UUID sessionId, ImportState state) implements ExtractVacancyImportResult {
    }

    record SessionNotFound(UUID sessionId) implements ExtractVacancyImportResult {
    }
}
