-- Sprint 9 Step 1: additive persistence foundation for Candidate Profile, skills and languages.
--
-- This migration only creates new, currently-unused tables. The runtime Candidate Profile
-- provider (ConfigurationCandidateProfileProvider) keeps reading from config/candidate-profile.yml
-- exactly as before; nothing in the application reads or writes these tables yet, and no row is
-- seeded here. Migrating real profile data onto this schema, and switching the runtime provider
-- over to it, is later Sprint 9 work.
CREATE TABLE candidate_profile (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_key             VARCHAR(100)   NOT NULL,
    target_role             VARCHAR(255)   NOT NULL,
    seniority               VARCHAR(50),
    experience_years        INTEGER        NOT NULL,
    preferred_company_type  VARCHAR(100),
    preferred_location      VARCHAR(300),
    employment_model        VARCHAR(50),
    remote_policy           VARCHAR(20),
    salary_currency         VARCHAR(3),
    minimum_salary          NUMERIC(12, 2),
    created_at              TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP      NOT NULL DEFAULT now(),
    version                 BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_candidate_profile_profile_key UNIQUE (profile_key),

    -- profile_key is the stable business key later steps will look profiles up by (e.g.
    -- "primary"); it is intentionally not the primary key, so more than one profile can exist
    -- without redesigning this schema.
    CONSTRAINT chk_candidate_profile_profile_key_not_blank
        CHECK (length(btrim(profile_key)) > 0),

    CONSTRAINT chk_candidate_profile_target_role_not_blank
        CHECK (length(btrim(target_role)) > 0),

    CONSTRAINT chk_candidate_profile_experience_years_non_negative
        CHECK (experience_years >= 0),

    CONSTRAINT chk_candidate_profile_minimum_salary_non_negative
        CHECK (minimum_salary IS NULL OR minimum_salary >= 0),

    CONSTRAINT chk_candidate_profile_salary_currency_format
        CHECK (salary_currency IS NULL OR salary_currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_candidate_profile_version_non_negative
        CHECK (version >= 0)
);

-- A skill the candidate does not possess is represented by the absence of a row here - there is
-- no "NONE" proficiency and no nullable proficiency column.
CREATE TABLE candidate_profile_skill (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id   UUID          NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    skill_name             VARCHAR(255)  NOT NULL,
    category               VARCHAR(100),
    proficiency            VARCHAR(20)   NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    -- Same skill name may repeat across different profiles, but not twice within one profile.
    CONSTRAINT uk_candidate_profile_skill_profile_id_skill_name UNIQUE (candidate_profile_id, skill_name),

    -- Not lowercased: technology names carry meaningful capitalization (Java, PostgreSQL, AWS).
    CONSTRAINT chk_candidate_profile_skill_skill_name_not_blank
        CHECK (length(btrim(skill_name)) > 0),

    CONSTRAINT chk_candidate_profile_skill_proficiency
        CHECK (proficiency IN ('BASIC', 'WORKING', 'STRONG', 'EXPERT'))
);

-- Postgres does not automatically index foreign-key columns; this backs both cascade deletes and
-- future profile-scoped skill loading.
CREATE INDEX idx_candidate_profile_skill_candidate_profile_id
    ON candidate_profile_skill (candidate_profile_id);

CREATE TABLE candidate_profile_language (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id   UUID          NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    language_code          VARCHAR(10)   NOT NULL,
    -- No language-proficiency model exists anywhere in the project yet (the current YAML/domain
    -- CandidateProfile.languages is a plain List<String>), so this intentionally stays a free,
    -- unconstrained nullable column rather than inventing a one-off enum/CHECK in this step.
    proficiency            VARCHAR(50),
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT uk_candidate_profile_language_profile_id_language_code UNIQUE (candidate_profile_id, language_code),

    -- Lowercase-only, letters-only (e.g. en, ru, pl); ISO 639-1/639-2 codes are 2-3 letters. The
    -- database rejects anything else outright rather than normalizing it - normalization is
    -- explicit application-code work for a later step.
    CONSTRAINT chk_candidate_profile_language_code_format
        CHECK (language_code ~ '^[a-z]{2,3}$')
);

CREATE INDEX idx_candidate_profile_language_candidate_profile_id
    ON candidate_profile_language (candidate_profile_id);
