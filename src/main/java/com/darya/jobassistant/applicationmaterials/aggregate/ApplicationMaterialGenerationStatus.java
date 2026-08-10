package com.darya.jobassistant.applicationmaterials.aggregate;

/**
 * Lifecycle state of one {@link ApplicationMaterialGeneration} attempt. Exactly the four states
 * currently required - no speculative workflow states (e.g. queued/retrying) are introduced here;
 * those belong to whichever later Sprint 10 step actually needs them.
 *
 * <p>Legal transitions: {@code PENDING -> IN_PROGRESS -> COMPLETED} or {@code PENDING ->
 * IN_PROGRESS -> FAILED} - see {@link ApplicationMaterialGeneration#start}/{@link
 * ApplicationMaterialGeneration#complete}/{@link ApplicationMaterialGeneration#fail}.
 */
public enum ApplicationMaterialGenerationStatus {

    /** Requested but not yet started - no generation work has begun. */
    PENDING,

    /** Generation is actively in flight. */
    IN_PROGRESS,

    /** Generation finished successfully. Terminal. */
    COMPLETED,

    /** Generation finished unsuccessfully - see {@link ApplicationMaterialGeneration#failureCode}. Terminal. */
    FAILED
}
