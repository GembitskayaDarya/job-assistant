-- Sprint 10 Step 1: persistence foundation for the Application Materials (tailored CV/cover
-- letter) generation workflow - one row per generation attempt for a Vacancy. A Vacancy may have
-- many generations over time (e.g. regenerated after Candidate Profile or Career History
-- changes), so this is intentionally a one-to-many child table, not columns on vacancy - see
-- ApplicationMaterialGeneration's javadoc.
--
-- This migration only creates lifecycle/metadata columns: no generated CV/cover-letter content
-- and no binary document. There is no established JSON/structured-content persistence convention
-- elsewhere in this codebase to reuse (job_analysis uses plain text/text[] columns, not JSONB),
-- so inventing one here would be premature - the structured content schema is later Sprint 10
-- work, added as a further additive migration once the AI generation contract exists.
--
-- vacancy_id FK is ON DELETE CASCADE, matching every existing vacancy-owned child table in this
-- codebase (job_analysis - V4, vacancy_recommendation_task - V15): a generation attempt has no
-- meaning once its vacancy is gone, and there is no cross-vacancy reference to preserve.
CREATE TABLE application_material_generation (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id                  UUID          NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    status                      VARCHAR(20)   NOT NULL,

    -- The candidate/profile context this generation attempt used (or, for a not-yet-started
    -- PENDING row, was requested against) - never the complete profile/history graph, which
    -- remains sourced from candidate_profile/career_history at generation time. Candidate Profile
    -- is mandatory context (CandidateContextSnapshot.candidateProfileVersion is non-nullable), so
    -- candidate_profile_version is NOT NULL; Career History is optional
    -- (CareerHistoryAvailability.NOT_PROVIDED is a valid state), so career_history_version is
    -- nullable.
    candidate_profile_version   BIGINT        NOT NULL,
    career_history_version      BIGINT,

    requested_at                TIMESTAMP     NOT NULL,
    started_at                  TIMESTAMP,
    completed_at                TIMESTAMP,

    -- Safe, bounded failure metadata only - never a stack trace, raw AI-provider exception,
    -- token, or other sensitive/infrastructure detail. failure_code is a short stable code (e.g.
    -- an enum-like reason); failure_message is an optional, length-capped human-readable detail.
    failure_code                VARCHAR(100),
    failure_message             VARCHAR(500),

    created_at                  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP     NOT NULL DEFAULT now(),
    version                     BIGINT        NOT NULL DEFAULT 0,

    -- Constraint names use the "amg" abbreviation (Application Material Generation), not the
    -- full table name: PostgreSQL silently truncates identifiers over 63 bytes, and
    -- "application_material_generation"-prefixed names for several of these checks exceed that
    -- limit - a truncated name is still accepted by CREATE TABLE but breaks exact-name lookups
    -- (e.g. this migration's own tests asserting on constraint name in an error message).
    CONSTRAINT chk_amg_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_amg_candidate_profile_version_non_negative
        CHECK (candidate_profile_version >= 0),

    CONSTRAINT chk_amg_career_history_version_non_negative
        CHECK (career_history_version IS NULL OR career_history_version >= 0),

    CONSTRAINT chk_amg_version_non_negative
        CHECK (version >= 0),

    -- Lifecycle invariants - see ApplicationMaterialGeneration's javadoc for the full state table.
    -- started_at is set exactly once a generation has left PENDING (IN_PROGRESS/COMPLETED/FAILED),
    -- never while still PENDING.
    CONSTRAINT chk_amg_started_at_matches_status
        CHECK ((status = 'PENDING') = (started_at IS NULL)),

    -- completed_at is set exactly for the two terminal statuses.
    CONSTRAINT chk_amg_completed_at_matches_status
        CHECK ((status IN ('COMPLETED', 'FAILED')) = (completed_at IS NOT NULL)),

    -- failure_code is set exactly for FAILED - never for PENDING/IN_PROGRESS/COMPLETED.
    CONSTRAINT chk_amg_failure_code_matches_status
        CHECK ((status = 'FAILED') = (failure_code IS NOT NULL)),

    -- failure_message is optional extra detail, only meaningful alongside a failure_code.
    CONSTRAINT chk_amg_failure_message_requires_code
        CHECK (failure_message IS NULL OR failure_code IS NOT NULL),

    -- Each lifecycle milestone, when present, is no earlier than the one before it.
    CONSTRAINT chk_amg_started_after_requested
        CHECK (started_at IS NULL OR started_at >= requested_at),

    CONSTRAINT chk_amg_completed_after_started
        CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

-- Backs both the FK join and "find generations for a Vacancy, newest first" listing
-- (ApplicationMaterialGenerationRepositoryPort#findByVacancyId) in one index.
CREATE INDEX idx_application_material_generation_vacancy_requested_at
    ON application_material_generation (vacancy_id, requested_at DESC);
