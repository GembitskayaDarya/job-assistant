package com.darya.jobassistant.ai.entity;

/**
 * Persistence-only concurrency state for a {@link JobAnalysisEntity} row. Never crosses into the
 * application API - {@code AnalyzeVacancyResult} expresses "in progress" as its own result type
 * instead, so callers never need to know this enum exists.
 */
public enum AnalysisStatus {
    IN_PROGRESS,
    COMPLETED
}
