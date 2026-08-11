package com.darya.jobassistant.applicationmaterials.preparation;

import java.util.UUID;

/**
 * Sprint 10 Step 5: {@link PrepareApplicationPackageUseCase#prepare}'s successful return value - a
 * framework-free bundle of the two ready-to-deliver documents for one {@code
 * ApplicationMaterialGeneration}, plus enough metadata for a caller to log/report without
 * re-deriving it. {@link #reusedExistingGeneration} is {@code true} exactly when an already
 * {@code COMPLETED} generation matching the caller's current Candidate Profile/Career History
 * versions was found and reused without any new AI request - see that method's javadoc for the
 * full reuse algorithm.
 */
public record PreparedApplicationPackage(
        UUID generationId,
        boolean reusedExistingGeneration,
        PreparedDocument cv,
        PreparedDocument coverLetter
) {

    public PreparedApplicationPackage {
        if (generationId == null) {
            throw new IllegalArgumentException("Prepared application package generationId must not be null");
        }
        if (cv == null) {
            throw new IllegalArgumentException("Prepared application package cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Prepared application package coverLetter must not be null");
        }
    }
}
