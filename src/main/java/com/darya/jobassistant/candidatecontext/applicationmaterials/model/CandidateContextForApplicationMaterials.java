package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidateProfile;
import java.util.List;

/**
 * Sprint 10 Step 2: the bounded, vacancy-specific, framework-free projection a future tailored
 * CV/cover-letter generation step renders into an AI prompt - never the raw {@code
 * com.darya.jobassistant.candidatecontext.CandidateContextSnapshot} or {@code
 * CareerHistoryAggregate}. Produced only by {@code
 * CandidateContextForApplicationMaterialsSelector#select}, a deterministic, AI-free, DB-free
 * operation, and version-validated by {@code ApplicationMaterialsCandidateContextProvider} before
 * that selection ever runs.
 *
 * <p>A deliberately separate type from {@code candidatecontext.analysis.CandidateContextForAnalysis}:
 * the two features need different amounts and shapes of candidate evidence (this one keeps the
 * company/position nesting intact and carries source-row provenance ids; analysis flattens company
 * into position text and carries no ids at all) - see {@link SelectedCareerCompany}'s javadoc.
 *
 * <p>{@link #candidateProfile} reuses the existing analysis-oriented {@link CandidateProfile}
 * projection rather than inventing a second one - it already carries every candidate-profile fact
 * this feature needs (target role, seniority, skills with proficiency, languages, experience
 * years, preferences) and is already an established framework-free type safe to hand to an AI
 * adapter.
 *
 * <p>{@link #selectedCompanies} is always a valid, non-null (possibly empty) list - empty exactly
 * when {@link #careerHistoryAvailability} is {@link CareerHistoryAvailability#NOT_PROVIDED} or
 * {@link CareerHistoryAvailability#EMPTY}. No fictional company/position/project is ever present
 * here - every entry traces back to a real source row via its provenance id.
 */
public record CandidateContextForApplicationMaterials(
        CandidateProfile candidateProfile,
        CareerHistoryAvailability careerHistoryAvailability,
        List<SelectedCareerCompany> selectedCompanies,
        CandidateContextForApplicationMaterialsSelectionMetadata selectionMetadata
) {

    public CandidateContextForApplicationMaterials {
        if (candidateProfile == null) {
            throw new IllegalArgumentException("Candidate context for application materials candidate profile must not be null");
        }
        if (careerHistoryAvailability == null) {
            throw new IllegalArgumentException(
                    "Candidate context for application materials career history availability must not be null");
        }
        if (selectionMetadata == null) {
            throw new IllegalArgumentException("Candidate context for application materials selection metadata must not be null");
        }
        selectedCompanies = selectedCompanies == null ? List.of() : List.copyOf(selectedCompanies);
    }
}
