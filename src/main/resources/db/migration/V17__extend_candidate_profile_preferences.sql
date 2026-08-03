-- Sprint 9 Step 3: additive extension so the Candidate Profile aggregate can losslessly represent
-- everything the YAML-oriented CandidatePreferences model carries. V16 is unchanged; this
-- migration only adds new nullable/defaulted columns and one new child table.
--
-- Four Step 1 flat columns on candidate_profile (preferred_company_type, remote_policy,
-- preferred_location, employment_model) are deliberately NOT touched here and are not populated
-- by the Step 3 migration: preferred_company_type/remote_policy would otherwise be a second,
-- independently-writable representation of the same concept the new candidate_profile_preference
-- rows (COMPANY_TYPE/WORK_ARRANGEMENT) now own - see CandidateProfileAggregate's javadoc for the
-- contradiction guard that keeps them from ever disagreeing. preferred_location/employment_model
-- have no lossless YAML source at all (YAML has no single "preferred location" string, and
-- employment_model was singular where the YAML preferred-contract-types is a list).

-- Scalar candidate_profile preferences that have no importance/weight and no repetition, so a
-- flat column is the correct (not merely convenient) representation for them - see the schema
-- design rule in the Step 3 task.
ALTER TABLE candidate_profile
    ADD COLUMN current_country         VARCHAR(100),
    ADD COLUMN relocation_allowed      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN salary_expectation_note VARCHAR(500);

-- CandidateSkill.note (free-text, e.g. "10+ years commercial use") has no column in V16 - absent
-- from analysis (JobAnalysisService.formatSkills only ever reads name/proficiency) but still a
-- real, configurable YAML field that must not be silently discarded by the migration.
ALTER TABLE candidate_profile_skill
    ADD COLUMN note VARCHAR(500);

-- Weighted/repeatable preferences: preferred work arrangement, allowed work countries, preferred
-- contract types, preferred company type. These four genuinely share one shape (a value, optionally
-- an importance, zero or more rows per profile) so one normalized child table is the smallest
-- schema that preserves their actual semantics - see the Step 3 task's EAV-avoidance rule. A fifth,
-- structurally different category (current_country/relocation_allowed/salary_expectation_note -
-- plain scalars, no importance, no repetition) deliberately stays as flat candidate_profile columns
-- above rather than being forced into this table.
CREATE TABLE candidate_profile_preference (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id   UUID          NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    preference_type        VARCHAR(30)   NOT NULL,
    preference_value       VARCHAR(255)  NOT NULL,
    -- Nullable: ALLOWED_WORK_COUNTRY has no importance dimension in the source model at all (it is
    -- a plain eligibility list) - forcing a value here would introduce information the source data
    -- never had. The cross-column CHECK below ties nullability to preference_type exactly, rather
    -- than leaving it to application code to keep consistent.
    importance             VARCHAR(20),
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_candidate_profile_preference_type
        CHECK (preference_type IN ('WORK_ARRANGEMENT', 'ALLOWED_WORK_COUNTRY', 'CONTRACT_TYPE', 'COMPANY_TYPE')),

    CONSTRAINT chk_candidate_profile_preference_value_not_blank
        CHECK (length(btrim(preference_value)) > 0),

    CONSTRAINT chk_candidate_profile_preference_importance
        CHECK (importance IS NULL OR importance IN ('REQUIRED', 'STRONG', 'PREFERRED', 'NEUTRAL')),

    -- ALLOWED_WORK_COUNTRY rows always have a null importance; every other type always has one.
    -- WORK_ARRANGEMENT/COMPANY_TYPE being single-valued (at most one row per profile) and
    -- CONTRACT_TYPE's importance being shared across its list are both enforced in application code
    -- (CandidateProfileAggregate), not here - the database's role is the shape/format invariants.
    CONSTRAINT chk_candidate_profile_preference_importance_matches_type
        CHECK ((preference_type = 'ALLOWED_WORK_COUNTRY') = (importance IS NULL)),

    -- Same value cannot be listed twice for the same type within one profile (e.g. "Poland" twice
    -- under ALLOWED_WORK_COUNTRY); the same value/type pair may repeat across different profiles.
    CONSTRAINT uk_candidate_profile_preference_profile_type_value
        UNIQUE (candidate_profile_id, preference_type, preference_value)
);

CREATE INDEX idx_candidate_profile_preference_candidate_profile_id
    ON candidate_profile_preference (candidate_profile_id);
