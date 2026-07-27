package com.darya.jobassistant.ai.model;

/**
 * The current version of the AI prompt/output interpretation that {@code JobAnalysisService}
 * produces - bumped only when prompt semantics or the {@link JobAnalysis} content contract change
 * in a way that makes a previously completed analysis worth recalculating. Deliberately unrelated
 * to the application's build/release version or to timestamps: it is a single fact about the
 * current code, defined in exactly one place, not an operational setting - there is no
 * environment property for it.
 *
 * <p>Analyses persisted before this versioning concept existed are treated as legacy version 1;
 * {@link #CURRENT} starts at 2 to leave that value free for them.
 */
public final class JobAnalysisModelVersion {

    public static final int CURRENT = 2;

    private JobAnalysisModelVersion() {
    }
}
