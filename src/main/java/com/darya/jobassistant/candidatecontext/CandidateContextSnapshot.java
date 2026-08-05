package com.darya.jobassistant.candidatecontext;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 9 Step 8: framework-free, immutable snapshot of everything vacancy analysis may draw on
 * for one candidate - the existing analysis-oriented {@link CandidateProfile} plus an optional
 * {@link CareerHistoryAggregate}, read together inside one consistency boundary by {@link
 * CandidateContextProvider#loadCurrentContext()}. This is reusable candidate state, not an AI
 * prompt - see {@code candidatecontext.analysis.CandidateContextForAnalysis} for the bounded,
 * vacancy-specific projection that is actually rendered into a prompt.
 *
 * <p>Never exposes a JPA entity or a {@code @ConfigurationProperties} object. {@link #careerHistory}
 * is {@link Optional#empty()} when Career History has never been imported for this candidate -
 * that is a fully valid, non-error state (see {@link CareerHistoryAvailability#NOT_PROVIDED}).
 */
public record CandidateContextSnapshot(
        UUID candidateProfileId,
        String profileKey,
        long candidateProfileVersion,
        CandidateProfile candidateProfile,
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
