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
 *
 * <h2>Sprint 9 Step 9 rollout note</h2>
 *
 * Sprint 9 Step 8 integrated optional Career History evidence into the prompt but deliberately
 * left {@link #CURRENT} at {@code 2} - no real Career History exists yet, so there is nothing for
 * the enriched prompt to evaluate, and bumping this would trigger automatic reanalysis (via the
 * existing reanalysis-claim mechanism in {@code AnalyzeVacancyService}) of every already-completed
 * vacancy for no benefit. Once real Career History has been imported and manually verified (Step
 * 9), bump {@link #CURRENT} from {@code 2} to {@code 3} as its own deliberate step:
 *
 * <ol>
 *   <li>bump {@code CURRENT} from 2 to 3;
 *   <li>run one controlled smoke analysis ({@code /analyze} against a single known vacancy) to
 *       confirm the enriched prompt behaves as expected against real data;
 *   <li>allow the existing reanalysis-claim mechanism to upgrade older analyses over time - no new
 *       invalidation mechanism is needed for this.
 * </ol>
 */
public final class JobAnalysisModelVersion {

    public static final int CURRENT = 2;

    private JobAnalysisModelVersion() {
    }
}
