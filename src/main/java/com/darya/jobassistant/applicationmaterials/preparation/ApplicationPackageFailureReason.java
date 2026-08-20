package com.darya.jobassistant.applicationmaterials.preparation;

/**
 * Sprint 11 Big Block 7 (Part 11): the safe, Telegram-presentable failure category behind a {@link
 * PrepareApplicationPackageOutcome.Failed} - lets the delivery channel show a specific, actionable
 * message ("configure your candidate profile" vs. "try again later") instead of one generic
 * catch-all, without leaking {@code ApplicationMaterialsGenerationFailureCode}/{@code
 * RenderApplicationMaterialsException.Reason} (internal, persistence-adjacent detail) past this
 * package's boundary. {@link PrepareApplicationPackageUseCase} is the only place that maps either of
 * those into one of these values - see its javadoc.
 */
public enum ApplicationPackageFailureReason {

    /** No Candidate Profile is configured at all for the runtime profile key. */
    CANDIDATE_CONTEXT_NOT_CONFIGURED,

    /** The current Candidate Profile/Career History no longer matches what the generation recorded. */
    CANDIDATE_CONTEXT_VERSION_MISMATCH,

    /** An AI provider request (CV tailoring or cover letter) failed for a network/provider-side reason. */
    AI_PROVIDER_ERROR,

    /** An AI provider response (CV tailoring or cover letter) could not be parsed into the expected structured shape. */
    MALFORMED_AI_RESPONSE,

    /** The AI's CV tailoring decisions failed source-aware validation against the candidate's factual data. */
    CV_TAILORING_VALIDATION_FAILED,

    /** The AI's cover letter failed provenance/structural validation against the candidate's factual data. */
    COVER_LETTER_VALIDATION_FAILED,

    /** The document renderer failed to produce PDF bytes from an otherwise-valid, validated document. */
    RENDERING_FAILED,

    /** The generated CV PDF failed structural ATS-readability verification and was never sent. */
    ATS_VERIFICATION_FAILED,

    /** The documents were generated but could not be loaded/delivered. */
    DOCUMENT_DELIVERY_FAILED,

    /** A controlled failure occurred for a reason not otherwise distinguished above. */
    UNKNOWN
}
