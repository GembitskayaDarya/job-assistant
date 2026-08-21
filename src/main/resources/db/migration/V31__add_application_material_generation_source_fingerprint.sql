-- Sprint 11 production hardening: replaces candidate_profile_version/career_history_version as
-- the reuse-validity authority for application_material_generation with one authoritative,
-- complete-source-state fingerprint (ApplicationMaterialSourceFingerprint#sha256 - candidate
-- profile facts, skills, education, languages, Career History, Personal Projects, and the vacancy
-- fields AI generation reads, composed into one SHA-256 digest). Root cause this closes: those two
-- version fields alone could not detect every source change that affects generated materials - a
-- Personal-Project-only edit left a COMPLETED generation looking "current" forever, since neither
-- version field changes for that edit.
--
-- source_fingerprint stays NULLable at the DB level deliberately: existing ("legacy") rows have no
-- trustworthy complete-source fingerprint at all (they were generated before this feature
-- existed), and backfilling them with the CURRENT fingerprint would falsely claim their already-
-- generated documents were produced from today's source state. The application/domain layer (see
-- ApplicationMaterialGeneration#requestNew) is what actually enforces "every newly requested
-- generation always has a real fingerprint" - a NOT NULL column constraint cannot distinguish
-- "legacy, correctly null" from "a bug forgot to set it", so this migration does not attempt one.
--
-- candidate_profile_version/career_history_version are NOT dropped - they remain persisted as
-- audit/debug metadata (see ApplicationMaterialGeneration's javadoc) - only their role as the
-- reuse-decision authority is retired, in application code, not schema.
-- VARCHAR, not CHAR: matches Hibernate's default mapping for a plain String field (see
-- ApplicationMaterialGenerationEntity#sourceFingerprint) and this table's own existing convention
-- for other fixed-shape string columns (e.g. failure_code) - CHAR's fixed-width blank-padding
-- semantics would otherwise fail Hibernate's schema validation against the VARCHAR it expects.
ALTER TABLE application_material_generation
    ADD COLUMN source_fingerprint VARCHAR(64);

-- Defensive format validation only (a 64-character lowercase hex SHA-256 digest) - never
-- interprets or compares content, matching every other CHECK on this table.
ALTER TABLE application_material_generation
    ADD CONSTRAINT chk_amg_source_fingerprint_format
        CHECK (source_fingerprint IS NULL OR source_fingerprint ~ '^[0-9a-f]{64}$');

-- Replaces V25's uk_amg_active_effective_key: the active-uniqueness concurrency guard must key on
-- exactly the same "effective source state" concept the reuse decision itself now uses (source
-- fingerprint), or two concurrent requests with genuinely different source content could be
-- incorrectly treated as racing for the same row - see
-- ApplicationMaterialGenerationActiveConflictException's javadoc. Same partial-index shape and
-- NULLS NOT DISTINCT rationale as V25 (COMPLETED/FAILED generations for the same key remain
-- unrestricted; only PENDING/IN_PROGRESS is covered) - source_fingerprint is expected to always be
-- non-null for any row this index actually compares in practice (every newly requested generation
-- always has one), but NULLS NOT DISTINCT is kept for the same defense-in-depth reason V25 had it.
DROP INDEX uk_amg_active_effective_key;

CREATE UNIQUE INDEX uk_amg_active_source_fingerprint
    ON application_material_generation (vacancy_id, source_fingerprint)
    NULLS NOT DISTINCT
    WHERE status IN ('PENDING', 'IN_PROGRESS');
