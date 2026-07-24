ALTER TABLE job_analysis
    ALTER COLUMN score DROP NOT NULL,
    ALTER COLUMN summary DROP NOT NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

-- Every row that existed before this migration is a genuine finished analysis (this table only
-- ever stored completed results until now), which is exactly what the one-time DEFAULT above
-- backfills them to. New rows must specify status explicitly - the claim-then-complete flow
-- always does - so the default is dropped immediately rather than left as an implicit default
-- for future inserts.
ALTER TABLE job_analysis ALTER COLUMN status DROP DEFAULT;

-- score/summary become nullable to support an IN_PROGRESS claim row: it exists solely to hold
-- the unique-per-vacancy slot (via uk_job_analysis_vacancy, already in place) while the AI call
-- is in flight, and has no result yet. chk_job_analysis_score is unaffected - PostgreSQL CHECK
-- constraints pass trivially on NULL.

-- Supports the stale-claim lookup: an IN_PROGRESS row whose updated_at is older than the
-- configured threshold is eligible to be reclaimed by the next caller instead of blocking
-- forever behind an abandoned attempt.
CREATE INDEX idx_job_analysis_status_updated_at
    ON job_analysis (status, updated_at)
    WHERE status = 'IN_PROGRESS';
