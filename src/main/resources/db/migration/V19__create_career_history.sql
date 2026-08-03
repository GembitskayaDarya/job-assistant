-- Sprint 9 Step 5: additive persistence foundation for Career History (companies, positions,
-- projects, and their ordered bullet/technology children), owned by Candidate Profile but a
-- separate aggregate - see CareerHistoryEntity's javadoc. This migration only creates new,
-- currently-unused tables: no runtime code reads or writes them yet (that is Step 6's
-- CareerHistoryRepositoryPort/adapter), and no row is seeded here. Candidate Profile can exist
-- with or without a Career History; normal startup is entirely unaffected.
--
-- Ownership chain, all FKs ON DELETE CASCADE:
--   candidate_profile -> career_history -> career_company -> career_position
--       -> career_position_responsibility / career_position_achievement
--       -> career_project -> career_project_responsibility / career_project_achievement / career_project_technology
--
-- FK constraints follow V16/V17's existing convention of inline REFERENCES ... ON DELETE CASCADE
-- (Postgres-generated constraint name) rather than an explicit CONSTRAINT name; every other
-- constraint (CHECK, UNIQUE) is explicitly named, also matching V16/V17.

-- One Candidate Profile has at most one Career History; Career History cannot exist without a
-- Candidate Profile. The UNIQUE constraint on candidate_profile_id both enforces that cardinality
-- and fully covers lookup-by-candidate_profile_id, so no separate index is added here (unlike the
-- child tables below, where a composite UNIQUE's leading column is not treated as a substitute for
-- an explicit single-column index - see idx_career_company_career_history_id etc., matching V16's
-- own candidate_profile_skill/candidate_profile_language convention).
CREATE TABLE career_history (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id    UUID        NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now(),
    version                 BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_career_history_candidate_profile_id UNIQUE (candidate_profile_id),

    CONSTRAINT chk_career_history_version_non_negative
        CHECK (version >= 0)
);

-- The same company name may exist across different Career Histories (different candidates may
-- both have worked at "Example Systems") - only unique within one Career History. No
-- case-insensitive uniqueness or name canonicalization here; that is later domain-layer work.
CREATE TABLE career_company (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_history_id   UUID          NOT NULL REFERENCES career_history (id) ON DELETE CASCADE,
    name                VARCHAR(255)  NOT NULL,
    website             VARCHAR(500),
    industry            VARCHAR(150),
    location            VARCHAR(300),
    description         TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_company_name_not_blank
        CHECK (length(btrim(name)) > 0),

    CONSTRAINT uk_career_company_history_id_name UNIQUE (career_history_id, name)
);

CREATE INDEX idx_career_company_career_history_id ON career_company (career_history_id);

-- start_date is required (a position with literally no known start date is not representable
-- here); end_date is optional even for a non-current role, since historical source data may be
-- incomplete - only a current role is forbidden from carrying one.
CREATE TABLE career_position (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_company_id   UUID          NOT NULL REFERENCES career_company (id) ON DELETE CASCADE,
    title               VARCHAR(255)  NOT NULL,
    employment_type     VARCHAR(50),
    location            VARCHAR(300),
    work_arrangement    VARCHAR(50),
    start_date          DATE          NOT NULL,
    end_date            DATE,
    -- Not named "current_role": that is a reserved SQL/PostgreSQL keyword (CURRENT_ROLE).
    is_current_role     BOOLEAN       NOT NULL DEFAULT false,
    description         TEXT,
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_position_title_not_blank
        CHECK (length(btrim(title)) > 0),

    CONSTRAINT chk_career_position_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT chk_career_position_end_date_after_start_date
        CHECK (end_date IS NULL OR end_date >= start_date),

    CONSTRAINT chk_career_position_current_role_has_no_end_date
        CHECK (NOT is_current_role OR end_date IS NULL),

    -- display_order is business data (resume ordering), not physical row order - explicit and
    -- unique per company, same convention as candidate_profile_preference's priority_order (V18).
    CONSTRAINT uk_career_position_company_id_display_order UNIQUE (career_company_id, display_order)
);

CREATE INDEX idx_career_position_career_company_id ON career_position (career_company_id);

CREATE TABLE career_position_responsibility (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_position_id     UUID          NOT NULL REFERENCES career_position (id) ON DELETE CASCADE,
    responsibility_text    VARCHAR(2000) NOT NULL,
    display_order          INTEGER       NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_position_responsibility_text_not_blank
        CHECK (length(btrim(responsibility_text)) > 0),

    CONSTRAINT chk_career_position_responsibility_display_order_non_negative
        CHECK (display_order >= 0),

    -- No uniqueness on responsibility_text itself: at VARCHAR(2000), a UNIQUE B-tree index could
    -- exceed PostgreSQL's per-index-entry size limit for legitimately long, multi-byte text -
    -- unlike the short name-like columns below (career_company.name, career_project.name,
    -- career_project_technology.technology_name), which stay well within that limit.
    CONSTRAINT uk_career_position_responsibility_position_id_display_order
        UNIQUE (career_position_id, display_order)
);

CREATE INDEX idx_career_position_responsibility_career_position_id
    ON career_position_responsibility (career_position_id);

-- Qualitative achievements (no numeric metric) are valid - no format constraint on the text.
CREATE TABLE career_position_achievement (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_position_id  UUID          NOT NULL REFERENCES career_position (id) ON DELETE CASCADE,
    achievement_text    VARCHAR(2000) NOT NULL,
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_position_achievement_text_not_blank
        CHECK (length(btrim(achievement_text)) > 0),

    CONSTRAINT chk_career_position_achievement_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT uk_career_position_achievement_position_id_display_order
        UNIQUE (career_position_id, display_order)
);

CREATE INDEX idx_career_position_achievement_career_position_id
    ON career_position_achievement (career_position_id);

-- Project dates are intentionally not constrained to fall inside the owning position's dates -
-- that cross-row invariant belongs to a later domain/application validation step, not the
-- database. Both dates are nullable (unlike career_position.start_date): a project's timeline is
-- frequently vaguer than the position that contains it.
CREATE TABLE career_project (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_position_id  UUID          NOT NULL REFERENCES career_position (id) ON DELETE CASCADE,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,
    start_date          DATE,
    end_date            DATE,
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_project_name_not_blank
        CHECK (length(btrim(name)) > 0),

    CONSTRAINT chk_career_project_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT chk_career_project_end_date_after_start_date
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),

    CONSTRAINT uk_career_project_position_id_display_order UNIQUE (career_position_id, display_order),

    CONSTRAINT uk_career_project_position_id_name UNIQUE (career_position_id, name)
);

CREATE INDEX idx_career_project_career_position_id ON career_project (career_position_id);

CREATE TABLE career_project_responsibility (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_project_id      UUID          NOT NULL REFERENCES career_project (id) ON DELETE CASCADE,
    responsibility_text    VARCHAR(2000) NOT NULL,
    display_order          INTEGER       NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_project_responsibility_text_not_blank
        CHECK (length(btrim(responsibility_text)) > 0),

    CONSTRAINT chk_career_project_responsibility_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT uk_career_project_responsibility_project_id_display_order
        UNIQUE (career_project_id, display_order)
);

CREATE INDEX idx_career_project_responsibility_career_project_id
    ON career_project_responsibility (career_project_id);

CREATE TABLE career_project_achievement (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_project_id   UUID          NOT NULL REFERENCES career_project (id) ON DELETE CASCADE,
    achievement_text    VARCHAR(2000) NOT NULL,
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_project_achievement_text_not_blank
        CHECK (length(btrim(achievement_text)) > 0),

    CONSTRAINT chk_career_project_achievement_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT uk_career_project_achievement_project_id_display_order
        UNIQUE (career_project_id, display_order)
);

CREATE INDEX idx_career_project_achievement_career_project_id
    ON career_project_achievement (career_project_id);

-- Not lowercased, matching candidate_profile_skill.skill_name's convention (V16): technology
-- names carry meaningful capitalization (Java, PostgreSQL, AWS). No global technology dictionary
-- is introduced here - technology_name is a free string scoped to one project.
CREATE TABLE career_project_technology (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    career_project_id   UUID          NOT NULL REFERENCES career_project (id) ON DELETE CASCADE,
    technology_name     VARCHAR(150)  NOT NULL,
    category            VARCHAR(100),
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_career_project_technology_name_not_blank
        CHECK (length(btrim(technology_name)) > 0),

    CONSTRAINT chk_career_project_technology_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT uk_career_project_technology_project_id_technology_name
        UNIQUE (career_project_id, technology_name),

    CONSTRAINT uk_career_project_technology_project_id_display_order
        UNIQUE (career_project_id, display_order)
);

CREATE INDEX idx_career_project_technology_career_project_id
    ON career_project_technology (career_project_id);
