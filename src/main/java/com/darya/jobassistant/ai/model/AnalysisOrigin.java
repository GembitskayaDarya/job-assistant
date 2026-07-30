package com.darya.jobassistant.ai.model;

/**
 * The workflow that created (or, after a reanalysis, most recently recalculated) a persisted
 * analysis - not where the vacancy itself came from. Never inferred from vacancy source, URL, or
 * provider.
 */
public enum AnalysisOrigin {

    /** {@code /analyze}, {@code /add}, guided import, or another explicit user-triggered analysis. */
    MANUAL,

    /** Started by the scheduled monitoring workflow. */
    MONITORING,

    /** Started by {@code VacancyRecommendationProcessingService} for an automatic web-discovery Vacancy. */
    AUTOMATIC_DISCOVERY,

    /** Row created before analysis origin was persisted - never assumed to be MANUAL or MONITORING. */
    LEGACY
}
