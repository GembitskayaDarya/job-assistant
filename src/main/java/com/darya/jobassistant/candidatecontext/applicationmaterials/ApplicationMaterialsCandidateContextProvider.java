package com.darya.jobassistant.candidatecontext.applicationmaterials;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException.Reason;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Sprint 10 Step 2: assembles the bounded, vacancy-aware {@link CandidateContextForApplicationMaterials}
 * for one {@link ApplicationMaterialGeneration}, after confirming the current Candidate Profile and
 * Career History still match the versions that generation recorded when it was requested.
 *
 * <h2>Version consistency</h2>
 *
 * {@link ApplicationMaterialGeneration#candidateProfileVersion()}/{@link
 * ApplicationMaterialGeneration#careerHistoryVersion()} record what the generation was requested
 * against, not a reloadable historical snapshot - neither repository stores version history. {@link
 * #loadContext} therefore always loads the <em>current</em> Candidate Profile/Career History (via
 * {@link CandidateContextProvider}, unchanged from Sprint 9) and compares versions; any mismatch -
 * including Career History having appeared or disappeared since the generation was requested -
 * fails fast with {@link CandidateContextVersionMismatchException} instead of silently generating
 * against different source data than what the generation recorded. This is a stale-context guard,
 * not a concurrency lock: nothing prevents a profile/history edit between this check and whatever a
 * caller does with the returned context afterward - the same best-effort freshness Sprint 9's
 * {@code PersistentCandidateContextProvider} already provides for vacancy analysis.
 *
 * <h2>Transaction boundary</h2>
 *
 * {@link #loadContext} is deliberately not itself {@code @Transactional}: {@link
 * CandidateContextProvider#loadCurrentContext()} opens and closes its own read-only transaction
 * internally (see {@code PersistentCandidateContextProvider}), so by the time this method receives
 * the snapshot, every database read is already complete - version validation and selection that
 * follow are plain in-memory operations. A future caller can therefore call {@link #loadContext},
 * let it return, and only then make an AI call, without ever holding a database transaction open
 * around that call.
 */
@Service
public class ApplicationMaterialsCandidateContextProvider {

    private final CandidateContextProvider candidateContextProvider;
    private final CandidateContextForApplicationMaterialsSelector selector;

    public ApplicationMaterialsCandidateContextProvider(
            CandidateContextProvider candidateContextProvider, CandidateContextForApplicationMaterialsSelector selector) {
        this.candidateContextProvider = candidateContextProvider;
        this.selector = selector;
    }

    /**
     * @throws CandidateContextVersionMismatchException if the current Candidate Profile/Career
     *     History no longer matches the versions {@code generation} recorded
     */
    public CandidateContextForApplicationMaterials loadContext(ApplicationMaterialGeneration generation, JobOffer vacancy) {
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        validateVersions(generation, snapshot);
        return selector.select(snapshot, vacancy);
    }

    /**
     * Checked in a fixed order - Candidate Profile first, then Career History presence, then
     * Career History version - so exactly one {@link Reason} is ever reported per call, even when
     * more than one thing has actually changed.
     */
    private void validateVersions(ApplicationMaterialGeneration generation, CandidateContextSnapshot snapshot) {
        long actualCandidateProfileVersion = snapshot.candidateProfileVersion();
        Long expectedCareerHistoryVersion = generation.careerHistoryVersion();
        Long actualCareerHistoryVersion = snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null);

        if (generation.candidateProfileVersion() != actualCandidateProfileVersion) {
            throw mismatch(Reason.CANDIDATE_PROFILE_VERSION_CHANGED, generation,
                    actualCandidateProfileVersion, expectedCareerHistoryVersion, actualCareerHistoryVersion);
        }
        if (expectedCareerHistoryVersion == null && actualCareerHistoryVersion != null) {
            throw mismatch(Reason.CAREER_HISTORY_NOW_PRESENT, generation,
                    actualCandidateProfileVersion, expectedCareerHistoryVersion, actualCareerHistoryVersion);
        }
        if (expectedCareerHistoryVersion != null && actualCareerHistoryVersion == null) {
            throw mismatch(Reason.CAREER_HISTORY_NOW_ABSENT, generation,
                    actualCandidateProfileVersion, expectedCareerHistoryVersion, actualCareerHistoryVersion);
        }
        if (expectedCareerHistoryVersion != null && !Objects.equals(expectedCareerHistoryVersion, actualCareerHistoryVersion)) {
            throw mismatch(Reason.CAREER_HISTORY_VERSION_CHANGED, generation,
                    actualCandidateProfileVersion, expectedCareerHistoryVersion, actualCareerHistoryVersion);
        }
    }

    private CandidateContextVersionMismatchException mismatch(
            Reason reason, ApplicationMaterialGeneration generation, long actualCandidateProfileVersion,
            Long expectedCareerHistoryVersion, Long actualCareerHistoryVersion) {
        return new CandidateContextVersionMismatchException(
                reason, generation.id(), generation.candidateProfileVersion(), actualCandidateProfileVersion,
                expectedCareerHistoryVersion, actualCareerHistoryVersion);
    }
}
