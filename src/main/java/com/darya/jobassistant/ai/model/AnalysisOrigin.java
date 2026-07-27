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

    /** Row created before analysis origin was persisted - never assumed to be MANUAL or MONITORING. */
    LEGACY
}
