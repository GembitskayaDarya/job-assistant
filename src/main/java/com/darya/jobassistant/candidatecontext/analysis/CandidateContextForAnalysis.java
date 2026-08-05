package com.darya.jobassistant.candidatecontext.analysis;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidateProfile;
import java.util.List;

/**
 * Sprint 9 Step 8: the bounded, vacancy-specific projection {@code JobAnalysisService} actually
 * renders into a prompt - never the raw {@code
 * com.darya.jobassistant.candidatecontext.CandidateContextSnapshot} or {@code
 * CareerHistoryAggregate}. Produced only by {@link CandidateContextForAnalysisSelector#select}, a
 * deterministic, AI-free, DB-free operation.
 *
 * <p>{@link #selectedPositions} is always a valid, non-null (possibly empty) list - empty exactly
 * when {@link #careerHistoryAvailability} is {@link CareerHistoryAvailability#NOT_PROVIDED} or
 * {@link CareerHistoryAvailability#EMPTY}. No fictional position/company is ever present here.
 */
public record CandidateContextForAnalysis(
        CandidateProfile candidateProfile,
        CareerHistoryAvailability careerHistoryAvailability,
        List<SelectedCareerPosition> selectedPositions,
        CandidateContextSelectionMetadata selectionMetadata
) {

    public CandidateContextForAnalysis {
        if (candidateProfile == null) {
            throw new IllegalArgumentException("Candidate context for analysis candidate profile must not be null");
        }
        if (careerHistoryAvailability == null) {
            throw new IllegalArgumentException("Candidate context for analysis career history availability must not be null");
        }
        if (selectionMetadata == null) {
            throw new IllegalArgumentException("Candidate context for analysis selection metadata must not be null");
        }
        selectedPositions = selectedPositions == null ? List.of() : List.copyOf(selectedPositions);
    }
}
