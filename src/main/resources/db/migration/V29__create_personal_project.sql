-- Sprint 11 Step 5: Personal Projects, persisted as their own independent aggregate root - one
-- row per project, not a single wrapper covering the candidate's whole collection (unlike Career
-- History). Each personal_project row owns its own optimistic-locking version and is saved
-- independently: a Candidate Profile save never touches this table, and saving one project never
-- rewrites another project's (or its own children's) identity. See PersonalProjectRepositoryPort
-- for the save contract this schema backs.
--
-- Fixed factual source data - a future AI tailoring step may select/order highlights and
-- technologies by their stable ids, but must never invent a project, a highlight, or a
-- technology.
CREATE TABLE personal_project (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id   UUID          NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    name                   VARCHAR(255)  NOT NULL,
    description            TEXT,
    url                    VARCHAR(500),
    start_date             DATE,
    end_date               DATE,
    display_order          INTEGER       NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_personal_project_name_not_blank
        CHECK (length(btrim(name)) > 0),

    CONSTRAINT chk_personal_project_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT chk_personal_project_end_date_after_start_date
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),

    CONSTRAINT chk_personal_project_version_non_negative
        CHECK (version >= 0)

    -- Deliberately no UNIQUE(candidate_profile_id, display_order): each Personal Project is an
    -- independent aggregate root saved on its own, so a cross-project uniqueness constraint would
    -- make a simple two-project reorder unsafe (the first of two swapped writes would violate the
    -- constraint before the second commits). Ordering is still deterministic - callers load with
    -- ORDER BY display_order ASC, id ASC - just not database-enforced-unique across projects.
    --
    -- Deliberately no UNIQUE(candidate_profile_id, name) either: a human-readable project name is
    -- presentation content, not identity - UUID is this aggregate's sole identity.
);

CREATE INDEX idx_personal_project_candidate_profile_id ON personal_project (candidate_profile_id);

-- Replaced atomically together with the rest of one project's own graph on that project's own
-- save (see PersonalProjectRepositoryAdapter) - never touches another project's rows.
CREATE TABLE personal_project_highlight (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_project_id UUID          NOT NULL REFERENCES personal_project (id) ON DELETE CASCADE,
    highlight_text      VARCHAR(2000) NOT NULL,
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_personal_project_highlight_text_not_blank
        CHECK (length(btrim(highlight_text)) > 0),

    CONSTRAINT chk_personal_project_highlight_display_order_non_negative
        CHECK (display_order >= 0),

    -- display_order uniqueness IS enforced here: unlike personal_project rows across projects,
    -- every highlight of one project is always replaced together in the same transaction as part
    -- of that one project's own save.
    CONSTRAINT uk_personal_project_highlight_project_id_display_order
        UNIQUE (personal_project_id, display_order)
);

CREATE INDEX idx_personal_project_highlight_personal_project_id
    ON personal_project_highlight (personal_project_id);

-- Duplicate technology names within one project are not meaningful (unlike duplicate project
-- names across a candidate's projects) - both database and domain layers reject them; the domain
-- layer additionally normalizes (trim + case-fold) before comparing, so "Kafka"/"kafka"/" Kafka "
-- cannot coexist as separate technology facts even though this plain-equality DB constraint alone
-- would allow it.
CREATE TABLE personal_project_technology (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_project_id UUID          NOT NULL REFERENCES personal_project (id) ON DELETE CASCADE,
    technology_name     VARCHAR(150)  NOT NULL,
    category            VARCHAR(100),
    display_order       INTEGER       NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_personal_project_technology_name_not_blank
        CHECK (length(btrim(technology_name)) > 0),

    CONSTRAINT chk_personal_project_technology_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT uk_personal_project_technology_project_id_technology_name
        UNIQUE (personal_project_id, technology_name),

    CONSTRAINT uk_personal_project_technology_project_id_display_order
        UNIQUE (personal_project_id, display_order)
);

CREATE INDEX idx_personal_project_technology_personal_project_id
    ON personal_project_technology (personal_project_id);
