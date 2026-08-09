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
 * {@link #CURRENT} started at 2 to leave that value free for them.
 *
 * <h2>Sprint 9 Step 9: bumped from 2 to 3</h2>
 *
 * Sprint 9 Step 8 integrated optional Career History evidence into the prompt but deliberately
 * left {@link #CURRENT} at {@code 2} until real Career History had actually been imported and
 * manually verified - bumping earlier would have triggered automatic reanalysis (via the existing
 * reanalysis-claim mechanism in {@code AnalyzeVacancyService}) of every already-completed vacancy
 * for no benefit, since there was nothing yet for the enriched prompt to evaluate. That
 * prerequisite is now complete, so {@link #CURRENT} moves to {@code 3}. This change alone performs
 * no database write and calls no AI: it only changes the eligibility test {@code
 * AnalyzeVacancyService#acquireForCompletedRow} applies to already-{@code COMPLETED} rows.
 * Existing {@code analysisVersion=2} rows stay exactly as they are - {@code 2}, with their
 * original content - until each is individually reanalyzed through the normal {@code /analyze}
 * workflow, one vacancy at a time, exactly as the reanalysis-claim mechanism already does for any
 * other outdated version.
 */
public final class JobAnalysisModelVersion {

    public static final int CURRENT = 3;

    private JobAnalysisModelVersion() {
    }
}
