-- Sprint 10 Step 4: persisted metadata for one rendered document artifact (a CV or cover letter
-- PDF) produced from a generation's render snapshot. Never stores the document bytes themselves -
-- those live on the configured local filesystem (see LocalFileStorageAdapter), addressed by
-- storage_key. PostgreSQL is the source of truth for "does this artifact exist and what are its
-- facts", never for the binary content itself.
--
-- generation_id FK is ON DELETE CASCADE, matching every other generation-owned child table
-- (V22/V23) - an artifact has no meaning once its generation is gone.
--
-- One generation may accumulate several artifacts over time - not just one CV and one cover
-- letter, but potentially the same material re-rendered under a later renderer_version (a new
-- template, still from the same immutable semantic generation, no new AI call) - so there is no
-- uk_...generation_id-only constraint the way V22/V23 have; instead the natural key is the
-- 4-column combination below.
CREATE TABLE application_material_artifact (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id       UUID          NOT NULL REFERENCES application_material_generation (id) ON DELETE CASCADE,
    material_type       VARCHAR(20)   NOT NULL,
    format              VARCHAR(20)   NOT NULL,
    renderer_version    INTEGER       NOT NULL,
    storage_key         VARCHAR(500)  NOT NULL,
    file_name           VARCHAR(255)  NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    size_bytes          BIGINT        NOT NULL,
    sha256_checksum     VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),

    -- The natural key: the same immutable generation may have this exact (material, format)
    -- combination rendered again only under a different renderer_version - never a second row for
    -- an identical combination. This is also what makes concurrent same-request rendering safe:
    -- exactly one of two racing inserts for the same 4 values can ever succeed (see
    -- ApplicationMaterialArtifactRepositoryAdapter).
    CONSTRAINT uk_application_material_artifact_natural_key
        UNIQUE (generation_id, material_type, format, renderer_version),

    CONSTRAINT chk_amart_material_type
        CHECK (material_type IN ('CV', 'COVER_LETTER')),

    CONSTRAINT chk_amart_format
        CHECK (format IN ('PDF')),

    CONSTRAINT chk_amart_renderer_version_positive
        CHECK (renderer_version > 0),

    CONSTRAINT chk_amart_size_bytes_positive
        CHECK (size_bytes > 0),

    -- Lowercase hex SHA-256: exactly 64 hex characters - matches HexFormat.of().formatHex's
    -- (lowercase) output, the same convention LocalFileStorageAdapter computes checksums with.
    CONSTRAINT chk_amart_sha256_checksum_format
        CHECK (sha256_checksum ~ '^[a-f0-9]{64}$'),

    CONSTRAINT chk_amart_storage_key_not_blank
        CHECK (length(btrim(storage_key)) > 0),

    CONSTRAINT chk_amart_file_name_not_blank
        CHECK (length(btrim(file_name)) > 0),

    CONSTRAINT chk_amart_content_type_not_blank
        CHECK (length(btrim(content_type)) > 0)
);

-- uk_application_material_artifact_natural_key above already covers both the FK/cascade path and
-- "list artifacts for a generation" (ApplicationMaterialArtifactRepositoryPort#findByGenerationId)
-- via its leading generation_id column, matching V16/V19/V22's "a UNIQUE constraint's leading
-- column doubles as the lookup index" convention - no separate index is added here.
