package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.net.URI;
import java.util.UUID;

/**
 * {@code UnexpectedState} only ever carries {@code EXTRACTING} or {@code WAITING_FOR_CONFIRMATION}:
 * {@code WAITING_FOR_URL} and {@code WAITING_FOR_DESCRIPTION} resolve to their own dedicated
 * accepted/invalid results instead, and a terminal state can never reach here because it can only
 * be produced from a session found by the active-session lookup.
 *
 * <p>There is no separate "description accepted" result: once a description is accepted the
 * session moves straight into {@code EXTRACTING} and AI extraction runs synchronously as part of
 * the same call, so a successful submission resolves directly to {@link VacancyRecognized} (or
 * {@link ExtractionFailed}) rather than a transient acknowledgement.
 */
public sealed interface ContinueVacancyImportResult {

    record UrlAccepted(UUID sessionId, URI sourceUrl) implements ContinueVacancyImportResult {
    }

    /** Also returned, with the same draft, when an already-recognized session is replayed. */
    record VacancyRecognized(UUID sessionId, VacancyImportDraft draft) implements ContinueVacancyImportResult {
    }

    record ExtractionFailed(UUID sessionId) implements ContinueVacancyImportResult {
    }

    record InvalidUrl(String safeReason) implements ContinueVacancyImportResult {
    }

    record InvalidDescription(String safeReason) implements ContinueVacancyImportResult {
    }

    record NoActiveSession() implements ContinueVacancyImportResult {
    }

    record SessionExpired() implements ContinueVacancyImportResult {
    }

    record UnexpectedState(ImportState state) implements ContinueVacancyImportResult {
    }
}
