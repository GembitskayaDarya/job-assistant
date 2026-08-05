package com.darya.jobassistant.candidatecontext.analysis;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;

/**
 * Sprint 9 Step 8 (final correction): safe, content-free counters and scalars describing what
 * {@link CandidateContextForAnalysisSelector} selected - the only Career-History-derived
 * information this feature ever logs (see {@code JobAnalysisService}). Never carries company
 * names, project names, responsibilities, achievements, or any other selected text.
 *
 * <p>{@link #careerHistoryAvailability} and {@link #careerHistoryVersion} are folded in here
 * (rather than read separately from {@code CandidateContextSnapshot}/{@code
 * CareerHistoryAggregate}) specifically so that {@code JobAnalysisService} - and everything under
 * {@code integrations.ai.openai} - never needs to reference either of those types: this record,
 * built entirely inside {@code candidatecontext.analysis}, is the one safe scalar carrier that
 * crosses that boundary. {@link #careerHistoryVersion} is {@code null} exactly when {@link
 * #careerHistoryAvailability} is {@link CareerHistoryAvailability#NOT_PROVIDED} - {@code EMPTY}
 * and {@code AVAILABLE} both correspond to a real, versioned {@code CareerHistoryAggregate} row.
 *
 * <p>{@link #truncated} is {@code true} only when at least one individual field's text was cut at
 * {@link CandidateContextAnalysisProperties#maxFieldCharacters()}, or the character-budget fill
 * stopped before every otherwise-eligible item could be included. A position/project excluded
 * purely because {@link CandidateContextAnalysisProperties#maxPositions()}/{@code maxProjects()}
 * was smaller than what was available is reflected only in {@link #omittedPositionCount()}/{@link
 * #omittedProjectCount()}, not in this flag.
 */
public record CandidateContextSelectionMetadata(
        CareerHistoryAvailability careerHistoryAvailability,
        Long careerHistoryVersion,
        int availablePositionCount,
        int selectedPositionCount,
        int availableProjectCount,
        int selectedProjectCount,
        int omittedPositionCount,
        int omittedProjectCount,
        int omittedResponsibilityCount,
        int omittedAchievementCount,
        int omittedTechnologyCount,
        int totalRenderedCharacters,
        boolean truncated
) {

    public CandidateContextSelectionMetadata {
        if (careerHistoryAvailability == null) {
            throw new IllegalArgumentException("Candidate context selection metadata careerHistoryAvailability must not be null");
        }
        if (careerHistoryAvailability == CareerHistoryAvailability.NOT_PROVIDED && careerHistoryVersion != null) {
            throw new IllegalArgumentException(
                    "Candidate context selection metadata careerHistoryVersion must be null when availability is NOT_PROVIDED");
        }
        if (careerHistoryAvailability != CareerHistoryAvailability.NOT_PROVIDED && careerHistoryVersion == null) {
            throw new IllegalArgumentException(
                    "Candidate context selection metadata careerHistoryVersion must not be null when Career History exists");
        }
        requireNonNegative(availablePositionCount, "availablePositionCount");
        requireNonNegative(selectedPositionCount, "selectedPositionCount");
        requireNonNegative(availableProjectCount, "availableProjectCount");
        requireNonNegative(selectedProjectCount, "selectedProjectCount");
        requireNonNegative(omittedPositionCount, "omittedPositionCount");
        requireNonNegative(omittedProjectCount, "omittedProjectCount");
        requireNonNegative(omittedResponsibilityCount, "omittedResponsibilityCount");
        requireNonNegative(omittedAchievementCount, "omittedAchievementCount");
        requireNonNegative(omittedTechnologyCount, "omittedTechnologyCount");
        requireNonNegative(totalRenderedCharacters, "totalRenderedCharacters");
    }

    /** Used for {@link CareerHistoryAvailability#NOT_PROVIDED}/{@code EMPTY} - no evidence was selected. */
    public static CandidateContextSelectionMetadata empty(CareerHistoryAvailability availability, Long careerHistoryVersion) {
        return new CandidateContextSelectionMetadata(
                availability, careerHistoryVersion, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException("Candidate context selection metadata " + fieldName + " must not be negative");
        }
    }
}
