package com.darya.jobassistant.careerhistory.importing;

/** The outcome classification {@link CareerHistoryImportUseCase} reports for one run. */
public enum CareerHistoryImportStatus {

    /** DRY_RUN only: no destination Career History exists yet - an APPLY would create one. */
    WOULD_CREATE,

    /** DRY_RUN only: a destination exists and is already semantically equal to the source. */
    WOULD_NO_OP,

    /** DRY_RUN only: a destination exists, differs from the source, and the supplied expectedVersion matches it - an APPLY would update it. */
    WOULD_UPDATE,

    /** DRY_RUN only: a destination exists and differs from the source, but expectedVersion is missing or does not match - an APPLY would refuse to write. */
    WOULD_CONFLICT,

    /** APPLY only: no destination existed; the complete graph was created and verified. */
    CREATED,

    /** APPLY only: a destination already existed and was already semantically equal - nothing was written. */
    NO_OP,

    /** APPLY only: a destination existed, differed, expectedVersion matched it, and the complete graph was replaced and verified. */
    UPDATED,

    /** APPLY only: a destination existed and differed, but expectedVersion was missing or did not match - refused, nothing was written. */
    CONFLICT
}
