package com.darya.jobassistant.vacancyimport.dto;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.UUID;

/**
 * Provider-independent outcome of {@link com.darya.jobassistant.vacancyimport.AnalyzeImportedVacancyUseCase}.
 * Carries {@link JobOffer} alongside the {@link JobAnalysis} in {@code Available} for the same
 * reason {@code AnalyzeVacancyResult.Available} does - so the Telegram callback handler can render
 * the shared formatter's output without loading a repository itself.
 */
public sealed interface AnalyzeImportedVacancyResult {

    record Available(UUID sessionId, UUID vacancyId, JobOffer vacancy, JobAnalysis analysis, boolean newlyCreated)
            implements AnalyzeImportedVacancyResult {
    }

    /** Session not found, or found but not owned by the calling chat/user - deliberately conflated. */
    record NotAvailable() implements AnalyzeImportedVacancyResult {
    }

    record InvalidState(ImportState state) implements AnalyzeImportedVacancyResult {
    }

    record MissingLinkedVacancy(UUID sessionId) implements AnalyzeImportedVacancyResult {
    }

    record InProgress() implements AnalyzeImportedVacancyResult {
    }

    record Failed() implements AnalyzeImportedVacancyResult {
    }
}
