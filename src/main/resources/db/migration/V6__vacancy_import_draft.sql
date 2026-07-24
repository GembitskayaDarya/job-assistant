CREATE TABLE vacancy_import_draft (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        UUID          NOT NULL REFERENCES vacancy_import_session (id) ON DELETE CASCADE,
    title             VARCHAR(300)  NOT NULL,
    company           VARCHAR(300)  NOT NULL,
    location          VARCHAR(300),
    remote_policy     VARCHAR(20)   NOT NULL,
    contract_types    TEXT[]        NOT NULL DEFAULT '{}',
    required_skills   TEXT[]        NOT NULL DEFAULT '{}',
    salary_text       VARCHAR(200),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    CONSTRAINT uk_vacancy_import_draft_session UNIQUE (session_id)
);

-- A session's draft is only ever meaningful together with its session (it is never displayed,
-- queried, or reused independently), so ON DELETE CASCADE is the deliberate choice here, same
-- reasoning as job_analysis's cascade to vacancy in V4__job_analysis.sql. Sessions themselves are
-- never deleted by application code today (they end in a terminal state instead), so this cascade
-- is currently dormant but is the correct policy if a future cleanup job ever purges old sessions.
