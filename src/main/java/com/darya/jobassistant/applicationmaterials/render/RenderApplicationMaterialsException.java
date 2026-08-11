package com.darya.jobassistant.applicationmaterials.render;

import java.util.UUID;

/**
 * Sprint 10 Step 4: controlled failure for {@link RenderApplicationMaterialsUseCase#render} -
 * mirrors {@code CandidateContextVersionMismatchException}'s "one exception type, a nested {@link
 * Reason} enum" convention. Never thrown for the two benign concurrent-creation races (a lost
 * render-snapshot or artifact-metadata race is resolved silently by reloading the winner - see the
 * use case's javadoc); always safe to log/surface to a caller, never carrying a raw filesystem path,
 * stack trace, or other sensitive infrastructure detail.
 */
public class RenderApplicationMaterialsException extends RuntimeException {

    /** Which specific step of the rendering flow failed - see {@link RenderApplicationMaterialsUseCase}'s javadoc for the full flow. */
    public enum Reason {

        /** {@code ApplicationMaterialGeneration.status} is not {@code COMPLETED}. */
        GENERATION_NOT_COMPLETED,

        /** The generation is {@code COMPLETED} but has no persisted {@code ApplicationMaterialGenerationResult}. */
        SEMANTIC_RESULT_MISSING,

        /** No render snapshot exists yet, and the current Candidate Profile/Career History no longer matches the generation's expected versions. */
        CANDIDATE_CONTEXT_VERSION_MISMATCH_BEFORE_FIRST_SNAPSHOT,

        /** The document renderer failed to produce bytes from an otherwise-valid render snapshot. */
        RENDERING_FAILED,

        /** {@code FileStoragePort#store} failed for a reason other than a content conflict. */
        STORAGE_FAILED,

        /** {@code FileStoragePort#store} found different content already stored at the deterministic storage key. */
        STORAGE_CONTENT_CONFLICT,

        /** The render snapshot could not be persisted (a genuine failure, not a lost concurrent-creation race). */
        SNAPSHOT_PERSISTENCE_FAILED,

        /** The artifact metadata row could not be persisted (a genuine failure, not a lost concurrent-creation race). */
        ARTIFACT_METADATA_PERSISTENCE_FAILED
    }

    private final Reason reason;
    private final UUID generationId;

    public RenderApplicationMaterialsException(Reason reason, UUID generationId, String message) {
        this(reason, generationId, message, null);
    }

    public RenderApplicationMaterialsException(Reason reason, UUID generationId, String message, Throwable cause) {
        super("Rendering failed for generation '" + generationId + "' (" + reason + "): " + message, cause);
        this.reason = reason;
        this.generationId = generationId;
    }

    public Reason reason() {
        return reason;
    }

    public UUID generationId() {
        return generationId;
    }
}
