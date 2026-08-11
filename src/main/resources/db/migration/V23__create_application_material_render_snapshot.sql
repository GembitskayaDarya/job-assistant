-- Sprint 10 Step 4: persistence for the immutable canonical render snapshot - the resolved,
-- frozen-at-creation-time canonical CV/cover-letter content (company names, position titles,
-- employment dates, validated bullets) a generation's semantic result and exact Career History
-- context are assembled into exactly once. See ApplicationMaterialRenderSnapshot's javadoc for why
-- this must exist as a separate, write-once artifact from application_material_generation_result
-- (V22): the semantic result deliberately carries only provenance UUIDs, never canonical facts, so
-- rendering must resolve those facts against the exact Candidate Profile/Career History state that
-- was valid when the generation ran - which normal repositories cannot reconstruct once that state
-- has moved on. Once this snapshot exists, it is the sole source of truth for rendering that
-- generation, independent of any later Candidate Profile/Career History change.
--
-- Write-once, same convention as V22: no version/updated_at column - a snapshot is never
-- overwritten, only created once (or reused if concurrent creation raced and lost - see
-- ApplicationMaterialRenderSnapshotRepositoryAdapter).
--
-- generation_id FK is ON DELETE CASCADE, matching V21/V22's convention: a snapshot has no meaning
-- once its owning generation is gone. UNIQUE(generation_id) is the database-level enforcement of
-- "at most one render snapshot per generation."
CREATE TABLE application_material_render_snapshot (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id   UUID        NOT NULL REFERENCES application_material_generation (id) ON DELETE CASCADE,
    schema_version  INTEGER     NOT NULL,
    content         JSONB       NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT uk_application_material_render_snapshot_generation_id UNIQUE (generation_id),

    CONSTRAINT chk_amrs_schema_version_positive
        CHECK (schema_version > 0)
);

-- uk_application_material_render_snapshot_generation_id above already covers lookup-by-
-- generation_id (its leading, only column) - no separate index needed, matching V22's convention.
