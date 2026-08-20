package com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate;

import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 10 Step 4: framework-free domain aggregate for the immutable canonical render snapshot -
 * backed by V23's {@code application_material_render_snapshot} table. A generation has at most one
 * snapshot (enforced by {@code uk_application_material_render_snapshot_generation_id}); the
 * snapshot belongs to the generation and is never updated after creation - see {@link #create}.
 *
 * <p>{@link #content} always holds {@code RenderModelAssembler}'s output: canonical company names,
 * position titles, and employment dates already resolved from the exact Career History context
 * that was valid when the generation ran - never re-derived from a possibly-different current
 * Candidate Profile/Career History state. This is the whole point of persisting it separately from
 * {@code ApplicationMaterialGenerationResult} (V22): once this row exists, rendering that
 * generation again never needs to reload or re-validate candidate context at all.
 */
public record ApplicationMaterialRenderSnapshot(
        UUID id,
        UUID generationId,
        int schemaVersion,
        RenderableApplicationMaterials content,
        Instant createdAt
) {

    /**
     * The current render-model schema version every newly created snapshot is stamped with - see
     * {@link #create}. Independent of {@code ApplicationMaterialGenerationResult#CURRENT_SCHEMA_VERSION}
     * (the AI semantic result's own schema) and of a renderer/template version (a presentation
     * concern, not a content-shape one) - bumped only if {@link RenderableApplicationMaterials}'s
     * shape changes in a way a reader of already-persisted JSONB content needs to distinguish.
     * Bumped to 2 in Sprint 11 Big Block 7 when {@link RenderableApplicationMaterials#cv()} changed
     * from {@code RenderableCv} to {@code TailoredCvDocument} - no reader of a v1-stamped row exists
     * in production (no real generations predate this block).
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public ApplicationMaterialRenderSnapshot {
        if (generationId == null) {
            throw new IllegalArgumentException("Application material render snapshot generationId must not be null");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Application material render snapshot schemaVersion must be positive");
        }
        if (content == null) {
            throw new IllegalArgumentException("Application material render snapshot content must not be null");
        }
    }

    /**
     * Creates a newly assembled snapshot at {@link #CURRENT_SCHEMA_VERSION}, not yet persisted
     * ({@link #id}/{@link #createdAt} are {@code null}) - the only domain-safe way to build one for
     * {@link ApplicationMaterialRenderSnapshotRepositoryPort#save}.
     */
    public static ApplicationMaterialRenderSnapshot create(UUID generationId, RenderableApplicationMaterials content) {
        return new ApplicationMaterialRenderSnapshot(null, generationId, CURRENT_SCHEMA_VERSION, content, null);
    }
}
