package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.net.URI;
import java.util.UUID;

/**
 * {@code UnexpectedState} only ever carries an active, non-{@code WAITING_FOR_URL} state
 * (WAITING_FOR_DESCRIPTION, EXTRACTING or WAITING_FOR_CONFIRMATION): a terminal state can never
 * reach here because it can only be produced from a session found by the active-session lookup,
 * and {@code WAITING_FOR_URL} itself resolves to {@code Accepted} or {@code InvalidUrl} instead.
 */
public sealed interface ProvideVacancyUrlResult {

    record Accepted(UUID sessionId, URI sourceUrl) implements ProvideVacancyUrlResult {
    }

    record InvalidUrl(String safeReason) implements ProvideVacancyUrlResult {
    }

    record NoActiveSession() implements ProvideVacancyUrlResult {
    }

    record SessionExpired() implements ProvideVacancyUrlResult {
    }

    record UnexpectedState(ImportState state) implements ProvideVacancyUrlResult {
    }
}
