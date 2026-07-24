package com.darya.jobassistant.ai.dto;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;

/**
 * Provider-independent outcome of {@link com.darya.jobassistant.ai.AnalyzeVacancyUseCase}.
 * {@code Available} carries the {@link JobOffer} alongside the {@link JobAnalysis} - not just the
 * analysis - so callers (the {@code /analyze} command, the imported-vacancy callback) can render
 * the shared formatter's output without a second vacancy lookup of their own.
 */
public sealed interface AnalyzeVacancyResult {

    record Available(JobOffer vacancy, JobAnalysis analysis, boolean newlyCreated) implements AnalyzeVacancyResult {
    }

    record VacancyNotFound() implements AnalyzeVacancyResult {
    }

    record InProgress() implements AnalyzeVacancyResult {
    }

    record Failed() implements AnalyzeVacancyResult {
    }
}
