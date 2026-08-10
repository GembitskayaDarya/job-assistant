package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;

/**
 * Sprint 10 Step 2: safe, content-free counters and scalars describing what {@code
 * CandidateContextForApplicationMaterialsSelector} selected - mirrors {@code
 * candidatecontext.analysis.CandidateContextSelectionMetadata}'s "safe to log" convention, extended
 * with company-level counts since this feature (unlike vacancy analysis) keeps companies as a
 * distinct selected unit rather than folding them into position text. Never carries company names,
 * position titles, project names, responsibilities, achievements, or any other selected text.
 *
 * <p>{@link #careerHistoryVersion} is {@code null} exactly when {@link #careerHistoryAvailability}
 * is {@link CareerHistoryAvailability#NOT_PROVIDED} - {@code EMPTY} and {@code AVAILABLE} both
 * correspond to a real, versioned {@code CareerHistoryAggregate} row.
 */
public record CandidateContextForApplicationMaterialsSelectionMetadata(
        CareerHistoryAvailability careerHistoryAvailability,
        Long careerHistoryVersion,
        int availableCompanyCount,
        int selectedCompanyCount,
        int availablePositionCount,
        int selectedPositionCount,
        int availableProjectCount,
        int selectedProjectCount,
        int omittedCompanyCount,
        int omittedPositionCount,
        int omittedProjectCount,
        int omittedResponsibilityCount,
        int omittedAchievementCount,
        int omittedTechnologyCount,
        int totalRenderedCharacters,
        boolean truncated
) {

    public CandidateContextForApplicationMaterialsSelectionMetadata {
        if (careerHistoryAvailability == null) {
            throw new IllegalArgumentException(
                    "Candidate context for application materials selection metadata careerHistoryAvailability must not be null");
        }
        if (careerHistoryAvailability == CareerHistoryAvailability.NOT_PROVIDED && careerHistoryVersion != null) {
            throw new IllegalArgumentException(
                    "Candidate context for application materials selection metadata careerHistoryVersion "
                            + "must be null when availability is NOT_PROVIDED");
        }
        if (careerHistoryAvailability != CareerHistoryAvailability.NOT_PROVIDED && careerHistoryVersion == null) {
            throw new IllegalArgumentException(
                    "Candidate context for application materials selection metadata careerHistoryVersion "
                            + "must not be null when Career History exists");
        }
        requireNonNegative(availableCompanyCount, "availableCompanyCount");
        requireNonNegative(selectedCompanyCount, "selectedCompanyCount");
        requireNonNegative(availablePositionCount, "availablePositionCount");
        requireNonNegative(selectedPositionCount, "selectedPositionCount");
        requireNonNegative(availableProjectCount, "availableProjectCount");
        requireNonNegative(selectedProjectCount, "selectedProjectCount");
        requireNonNegative(omittedCompanyCount, "omittedCompanyCount");
        requireNonNegative(omittedPositionCount, "omittedPositionCount");
        requireNonNegative(omittedProjectCount, "omittedProjectCount");
        requireNonNegative(omittedResponsibilityCount, "omittedResponsibilityCount");
        requireNonNegative(omittedAchievementCount, "omittedAchievementCount");
        requireNonNegative(omittedTechnologyCount, "omittedTechnologyCount");
        requireNonNegative(totalRenderedCharacters, "totalRenderedCharacters");
    }

    /** Used for {@link CareerHistoryAvailability#NOT_PROVIDED}/{@code EMPTY} - no evidence was selected. */
    public static CandidateContextForApplicationMaterialsSelectionMetadata empty(
            CareerHistoryAvailability availability, Long careerHistoryVersion) {
        return new CandidateContextForApplicationMaterialsSelectionMetadata(
                availability, careerHistoryVersion, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Candidate context for application materials selection metadata " + fieldName + " must not be negative");
        }
    }
}
