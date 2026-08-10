-- Sprint 10 Step 3: persistence for the structured semantic CV/cover-letter content produced by
-- one ApplicationMaterialGeneration attempt. A separate one-to-zero-or-one table, not columns on
-- application_material_generation (V21): the lifecycle row tracks status/timestamps/versions
-- regardless of whether generation ever succeeds, while this row exists only once a generation has
-- actually produced a valid result - keeping the two concerns (lifecycle vs. content) independently
-- evolvable, matching V21's own "no generated content in Step 1" deferral.
--
-- Write-once: a result row is never updated after insert (a regeneration is represented by a new
-- ApplicationMaterialGeneration + new result row, never by overwriting this one - see
-- GenerateApplicationMaterialsUseCase's javadoc), so there is no version/updated_at column here.
--
-- generation_id FK is ON DELETE CASCADE, matching V21's own vacancy_id convention and every other
-- vacancy-owned child table in this codebase: a result has no meaning once its owning generation is
-- gone. UNIQUE(generation_id) is the database-level enforcement of "at most one semantic result per
-- generation" - CandidateContextForApplicationMaterialsRepositoryAdapter's application-level check
-- is a courtesy, not the sole guarantee.
--
-- cv_content/cover_letter_content are genuine PostgreSQL JSONB, not opaque Java serialization or a
-- TEXT blob - this is this project's first JSON-document persistence need (job_analysis and
-- vacancy_import_draft both use plain text[] columns for flat string lists, not a real nested
-- document - see those tables' own comments), so this migration establishes the convention rather
-- than reusing an unsuitable one. Serialization/deserialization goes through an explicit, stable
-- mapper (ApplicationMaterialGenerationResultContentMapper) - Hibernate never reflects over the
-- domain graph to build this column's shape automatically.
--
-- No raw OpenAI response, no prompt text, and no PDF/DOCX bytes are ever stored here - only the
-- already-validated structured semantic content plus safe, small AI/prompt provenance metadata.
CREATE TABLE application_material_generation_result (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    generation_id           UUID        NOT NULL REFERENCES application_material_generation (id) ON DELETE CASCADE,
    schema_version          INTEGER     NOT NULL,
    cv_content              JSONB       NOT NULL,
    cover_letter_content    JSONB       NOT NULL,
    ai_provider             VARCHAR(50)  NOT NULL,
    ai_model                VARCHAR(100) NOT NULL,
    prompt_version          INTEGER     NOT NULL,
    generated_at            TIMESTAMP   NOT NULL,
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT uk_application_material_generation_result_generation_id UNIQUE (generation_id),

    CONSTRAINT chk_amgr_schema_version_positive
        CHECK (schema_version > 0),

    CONSTRAINT chk_amgr_prompt_version_positive
        CHECK (prompt_version > 0),

    CONSTRAINT chk_amgr_ai_provider_not_blank
        CHECK (length(btrim(ai_provider)) > 0),

    CONSTRAINT chk_amgr_ai_model_not_blank
        CHECK (length(btrim(ai_model)) > 0),

    -- Each result is produced no earlier than its owning generation was created; generated_at is
    -- the AI response moment, created_at the (always slightly later or equal) persistence moment.
    CONSTRAINT chk_amgr_created_at_not_before_generated_at
        CHECK (created_at >= generated_at)
);

-- uk_application_material_generation_result_generation_id above already covers lookup-by-
-- generation_id (its leading, only column), matching V16/V19's "a UNIQUE constraint's leading
-- column doubles as the lookup index" convention - no separate index is added here.
