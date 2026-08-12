package com.darya.jobassistant.candidatecontext;

import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 9 Step 8, corrected in Sprint 11 Step 1: framework-free, immutable snapshot of the
 * complete candidate facts every downstream use case may draw on - {@link CandidateProfileFacts}
 * plus an optional {@link CareerHistoryAggregate}, read together inside one consistency boundary by
 * {@link CandidateContextProvider#loadCurrentContext()}. This is reusable candidate state, not an
 * AI prompt and not itself constrained by any single use case's needs.
 *
 * <p>Originally carried the vacancy-analysis-bounded {@code candidates.CandidateProfile} here
 * directly, which silently dropped skill category and language proficiency before any downstream
 * consumer - including non-analysis ones such as {@code candidatecontext.cv.CvSourceSnapshotFactory}
 * - ever saw them. {@link #candidateProfile} now carries the complete, lossless {@link
 * CandidateProfileFacts} instead; each bounded, vacancy-specific projection (see {@code
 * candidatecontext.analysis.CandidateContextForAnalysis}, {@code
 * candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials}) narrows it
 * explicitly at its own selector boundary via {@code
 * candidates.migration.CandidateProfileAnalysisAssembler#toAnalysisProfile(CandidateProfileFacts)}.
 *
 * <p>Never exposes a JPA entity or a {@code @ConfigurationProperties} object. {@link #careerHistory}
 * is {@link Optional#empty()} when Career History has never been imported for this candidate -
 * that is a fully valid, non-error state (see {@link CareerHistoryAvailability#NOT_PROVIDED}).
 */
public record CandidateContextSnapshot(
        UUID candidateProfileId,
        String profileKey,
        long candidateProfileVersion,
        CandidateProfileFacts candidateProfile,
        Optional<CareerHistoryAggregate> careerHistory
) {

    public CandidateContextSnapshot {
        if (candidateProfileId == null) {
            throw new IllegalArgumentException("Candidate context candidate profile id must not be null");
        }
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("Candidate context profile key must not be blank");
        }
        if (candidateProfileVersion < 0) {
            throw new IllegalArgumentException("Candidate context candidate profile version must not be negative");
        }
        if (candidateProfile == null) {
            throw new IllegalArgumentException("Candidate context candidate profile must not be null");
        }
        careerHistory = careerHistory == null ? Optional.empty() : careerHistory;
    }

    /**
     * Derives the three-state availability {@link CareerHistoryAvailability} from {@link
     * #careerHistory} - the one place this distinction is computed, so callers never re-derive it
     * from {@code companies().isEmpty()} themselves.
     */
    public CareerHistoryAvailability careerHistoryAvailability() {
        return careerHistory
                .map(history -> history.companies().isEmpty()
                        ? CareerHistoryAvailability.EMPTY
                        : CareerHistoryAvailability.AVAILABLE)
                .orElse(CareerHistoryAvailability.NOT_PROVIDED);
    }
}
