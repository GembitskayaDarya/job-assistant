CREATE TABLE job_analysis (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id      UUID         NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    score           INTEGER      NOT NULL,
    summary         TEXT         NOT NULL,
    pros            TEXT[]       NOT NULL DEFAULT '{}',
    cons            TEXT[]       NOT NULL DEFAULT '{}',
    missing_skills  TEXT[]       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_job_analysis_vacancy UNIQUE (vacancy_id),
    CONSTRAINT chk_job_analysis_score CHECK (score BETWEEN 0 AND 100)
);

-- Supports the notification-candidate backlog query's "score >= :minimumScore" filter and
-- "ORDER BY score DESC" clause. The unique constraint above already covers the join to vacancy
-- (one analysis per vacancy), and the existing uk_notification_delivery_vacancy_recipient index
-- already covers the query's NOT EXISTS lookup on notification_delivery, so no further indexes
-- are needed for that part of the query.
CREATE INDEX idx_job_analysis_score ON job_analysis (score);
