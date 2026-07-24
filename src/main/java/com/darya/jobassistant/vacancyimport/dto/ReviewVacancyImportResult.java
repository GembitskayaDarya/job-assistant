package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.UUID;

/**
 * Provider-independent outcome of {@link com.darya.jobassistant.vacancyimport.ReviewVacancyImportUseCase}.
 * Carries {@link JobOffer} rather than the {@code Vacancy} JPA entity - the same
 * already-established immutable read model {@code AnalyzeCommand} and {@code JobAnalysisService}
 * use, so this result stays free of persistence types without inventing a second one.
 *
 * <p>{@code Saved} and {@code Cancelled} double as the idempotent-repeat results for their
 * respective actions (a repeated Save returns the same {@code Saved} with {@code newlyCreated =
 * false}; a repeated Cancel returns the same {@code Cancelled}) rather than adding separate
 * "already" variants, since the Telegram-facing outcome is identical either way.
 */
public sealed interface ReviewVacancyImportResult {

    record Saved(UUID sessionId, JobOffer vacancy, boolean newlyCreated) implements ReviewVacancyImportResult {
    }

    record RetryRequested(UUID sessionId) implements ReviewVacancyImportResult {
    }

    record Cancelled(UUID sessionId) implements ReviewVacancyImportResult {
    }

    record Expired(UUID sessionId) implements ReviewVacancyImportResult {
    }

    record InvalidState(UUID sessionId, ImportState state) implements ReviewVacancyImportResult {
    }

    /** Session not found, or found but not owned by the calling chat/user - deliberately conflated. */
    record NotAvailable() implements ReviewVacancyImportResult {
    }

    record DraftMissing(UUID sessionId) implements ReviewVacancyImportResult {
    }

    record Failed() implements ReviewVacancyImportResult {
    }
}
