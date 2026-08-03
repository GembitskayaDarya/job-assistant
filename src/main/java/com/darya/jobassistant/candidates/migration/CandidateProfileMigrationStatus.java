package com.darya.jobassistant.candidates.migration;

/** The outcome classification {@link CandidateProfileMigrationUseCase} reports for one run. */
public enum CandidateProfileMigrationStatus {

    /** DRY_RUN only: no destination profile exists yet - an APPLY would create one. */
    WOULD_CREATE,

    /** DRY_RUN only: a destination profile exists and is already semantically equal to the source. */
    WOULD_NO_OP,

    /** DRY_RUN only: a destination profile exists and differs from the source - an APPLY would refuse to overwrite it. */
    WOULD_CONFLICT,

    /** APPLY only: no destination existed; the complete aggregate was created and verified. */
    CREATED,

    /** APPLY only: a destination already existed and was already semantically equal - nothing was written. */
    NO_OP,

    /** APPLY only: a destination already existed and differs from the source - refused, nothing was written. */
    CONFLICT,

    /** Either mode: the source profile could not be mapped to a valid aggregate - no read or write was attempted. */
    VALIDATION_FAILED
}
