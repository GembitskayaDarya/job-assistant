package com.darya.jobassistant.applicationmaterials.generation;

/**
 * Sprint 10 Step 3: what {@link GenerateApplicationMaterialsUseCase#generate} actually did for one
 * call - mirrors {@code CareerHistoryImportResult}'s "report an outcome, don't just throw"
 * convention, since more than one of these is a normal, expected result, not an exceptional one.
 */
public enum GenerationOutcomeStatus {

    /** This call generated and persisted a brand-new result, and completed the generation. */
    COMPLETED,

    /** The generation was already COMPLETED before this call - the existing result is returned unchanged, nothing was regenerated. */
    ALREADY_COMPLETED,

    /** The generation is currently IN_PROGRESS - already running, or this call lost a concurrent race to start it. */
    ALREADY_IN_PROGRESS,

    /** The generation was already FAILED before this call - not retried; a genuine retry needs a new generation. */
    ALREADY_FAILED,

    /** This call started the generation but it failed during this attempt - see the returned generation's failure code/message. */
    FAILED
}
