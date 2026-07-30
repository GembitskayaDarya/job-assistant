-- Sprint 8 Step 10: durable vacancy-analysis and Telegram-notification pipeline for automatic
-- web-discovery vacancies.
--
-- Part 1: job_analysis provenance/manual-review extensions.
--
-- analysis_origin (VARCHAR(20) NOT NULL, backed by chk_job_analysis_origin) already exists as of
-- V11 with allowed values 'MANUAL', 'MONITORING', 'LEGACY' - every pre-V11 row was already
-- backfilled to 'LEGACY' there. This migration only widens the existing CHECK constraint to also
-- allow the new 'AUTOMATIC_DISCOVERY' origin (used exclusively by the new recommendation-task
-- processing pipeline below); it does not touch analysis_origin's NOT NULL-ness, its column
-- definition, or any existing row's value. PostgreSQL has no ALTER CHECK - the constraint is
-- dropped and an equivalently-named one recreated with the wider allow-list, which is why this
-- migration, like V13, runs as one ordinary transactional block: a failure here leaves the
-- original, narrower constraint fully intact rather than a database with no constraint at all.
ALTER TABLE job_analysis
    DROP CONSTRAINT chk_job_analysis_origin;

ALTER TABLE job_analysis
    ADD CONSTRAINT chk_job_analysis_origin
    CHECK (analysis_origin IN ('MANUAL', 'MONITORING', 'LEGACY', 'AUTOMATIC_DISCOVERY'));

-- manually_reviewed_at is new and nullable for every row, existing and future: it records the
-- moment a human actually reviewed an analysis via /analyze (a new claim, a reused completed
-- analysis, or a reanalysis - see AnalyzeVacancyService), never inferred from analysis_origin or
-- any other existing column. No backfill is possible or attempted - a NULL value here means
-- "no manual review is known to have happened", which is the correct, conservative reading for
-- every row that predates this column.
ALTER TABLE job_analysis
    ADD COLUMN manually_reviewed_at TIMESTAMP;

-- Part 2: durable recommendation-processing task - one per web-discovery-created Vacancy.
--
-- This is an application work-item table, not a generic message broker or generic outbox: it has
-- exactly one row shape, one owning feature (VacancyRecommendationProcessingService), and no
-- pluggable message type. status/outcome/last_error_category are persisted as the exact stable
-- enum-name strings the application uses (see VacancyRecommendationTaskStatus/
-- VacancyRecommendationTaskOutcome/VacancyRecommendationFailureCategory) - never a JPA enum
-- ordinal, so the allowed-value CHECK constraints below stay meaningful on their own and a future
-- enum reordering can never silently corrupt stored data.
CREATE TABLE vacancy_recommendation_task (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id          UUID         NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    status              VARCHAR(20)  NOT NULL,
    outcome             VARCHAR(40),
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMP    NOT NULL,
    lease_until         TIMESTAMP,
    lease_owner         VARCHAR(255),
    last_error_category VARCHAR(40),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at        TIMESTAMP,

    CONSTRAINT uk_vacancy_recommendation_task_vacancy UNIQUE (vacancy_id),

    CONSTRAINT chk_vacancy_recommendation_task_attempt_count_non_negative
        CHECK (attempt_count >= 0),

    CONSTRAINT chk_vacancy_recommendation_task_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'COMPLETED', 'DEAD')),

    CONSTRAINT chk_vacancy_recommendation_task_outcome
        CHECK (outcome IN (
            'NOTIFIED', 'BELOW_SCORE_THRESHOLD', 'MANUALLY_REVIEWED', 'ALREADY_NOTIFIED',
            'ANALYSIS_ALREADY_EXISTS_NON_AUTOMATIC', 'PERMANENT_FAILURE'
        )),

    -- outcome is populated exactly for the two terminal statuses (COMPLETED, DEAD) and never
    -- otherwise - a PENDING/PROCESSING/RETRY_WAIT row has no outcome yet, and a terminal row
    -- always has one.
    CONSTRAINT chk_vacancy_recommendation_task_outcome_matches_status
        CHECK ((status IN ('COMPLETED', 'DEAD')) = (outcome IS NOT NULL)),

    -- completed_at mirrors outcome: set exactly when a task reaches a terminal status.
    CONSTRAINT chk_vacancy_recommendation_task_completed_at_matches_status
        CHECK ((status IN ('COMPLETED', 'DEAD')) = (completed_at IS NOT NULL)),

    -- lease_until/lease_owner are only meaningful while a task is actively claimed; a task that
    -- has never been claimed, or is currently PENDING/RETRY_WAIT/terminal, carries neither.
    CONSTRAINT chk_vacancy_recommendation_task_lease_matches_status
        CHECK ((status = 'PROCESSING') = (lease_until IS NOT NULL AND lease_owner IS NOT NULL))
);

-- Backs the claim query's WHERE clause (PENDING, due RETRY_WAIT, expired-lease PROCESSING) and its
-- ORDER BY next_attempt_at, created_at, id - a single index usable by all three eligibility
-- branches and the deterministic ordering together, so claiming never requires a full table scan
-- as the table grows.
CREATE INDEX idx_vacancy_recommendation_task_pending_work
    ON vacancy_recommendation_task (next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING');
