package com.darya.jobassistant.ai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A {@link JobAnalysis} as durably stored for a specific vacancy. {@link JobAnalysis} itself
 * stays a plain AI-result value (it is also used for the ephemeral, never-persisted result of
 * the {@code /analyze} command) - {@code vacancyId}, {@code analysisVersion} and {@code
 * analysisOrigin} live here rather than on {@link JobAnalysis} because they are persistence/
 * workflow metadata, not AI output: the AI never produces either value.
 *
 * @param manuallyReviewedAt present exactly when a human has actually reviewed this analysis via
 *     {@code /analyze} - never inferred from {@code analysisOrigin}; see {@code
 *     JobAnalysisEntity#manuallyReviewedAt} for the exact semantics.
 */
public record PersistedJobAnalysis(
        UUID id,
        UUID vacancyId,
        JobAnalysis analysis,
        int analysisVersion,
        AnalysisOrigin analysisOrigin,
        Instant createdAt,
        Instant manuallyReviewedAt
) {
    public PersistedJobAnalysis {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
        if (analysis == null) {
            throw new IllegalArgumentException("analysis must not be null");
        }
        if (analysisVersion <= 0) {
            throw new IllegalArgumentException("analysisVersion must be positive");
        }
        if (analysisOrigin == null) {
            throw new IllegalArgumentException("analysisOrigin must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    /** True when a human has actually reviewed this analysis via {@code /analyze}. */
    public boolean isManuallyReviewed() {
        return manuallyReviewedAt != null || analysisOrigin == AnalysisOrigin.MANUAL;
    }
}
