package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Sprint 10 Step 3: small, safe failure codes for {@link
 * com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration#failureCode()}
 * - the stable {@link #name()} string is what actually gets persisted (V21's {@code failure_code}
 * column has no CHECK constraint on its allowed values, deliberately left open by Step 1 for this
 * exact future need). Never a stack trace, raw provider error, or any other unsafe detail - see
 * {@code GenerateApplicationMaterialsUseCase}'s failure-message handling for what accompanies each
 * code.
 */
public enum ApplicationMaterialsGenerationFailureCode {

    /** The current Candidate Profile/Career History no longer matches what the generation recorded. */
    CANDIDATE_CONTEXT_VERSION_MISMATCH,

    /** Sprint 11 Big Block 7: no Candidate Profile is configured at all for the runtime profile key. */
    CANDIDATE_CONTEXT_NOT_CONFIGURED,

    /** The AI provider request itself failed (network, auth, rate limit, provider-side error). */
    AI_PROVIDER_ERROR,

    /** The AI response could not be parsed into the expected structured shape. */
    MALFORMED_AI_RESPONSE,

    /** The parsed cover-letter AI response failed deterministic provenance/structural validation. */
    RESULT_VALIDATION_FAILED,

    /** Sprint 11 Big Block 7: the CV tailoring AI response failed source-aware validation ({@code CvTailoringValidator}) - distinct from {@link #RESULT_VALIDATION_FAILED}, which now covers the cover letter only. */
    CV_TAILORING_VALIDATION_FAILED,

    /** A validated result could not be persisted, but a safe FAILED transition was still possible. */
    PERSISTENCE_FAILURE,

    /**
     * Sprint 10 Step 6: a previous process transitioned this generation to {@code IN_PROGRESS} and
     * then crashed or was abandoned before reaching {@code COMPLETED}/{@code FAILED} - recovered by
     * {@code PrepareApplicationPackageUseCase} once {@link
     * com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration#isStaleInProgress}
     * reports it exceeded the configured timeout. The old row is never reused for another AI
     * attempt (it may have completed or partially completed an external AI request before
     * crashing) - only ever marked {@code FAILED} as honest historical evidence, with a brand-new
     * generation created for the actual retry.
     */
    STALE_IN_PROGRESS
}
