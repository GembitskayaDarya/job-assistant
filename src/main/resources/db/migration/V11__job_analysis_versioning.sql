-- Step 7: analysis versioning, origin tracking, and a safe reanalysis claim - all
-- backward-compatible. No AI calls happen here; every existing row is preserved as-is.
ALTER TABLE job_analysis
    ADD COLUMN analysis_version           INTEGER      NOT NULL DEFAULT 1,
    ADD COLUMN analysis_origin            VARCHAR(20)  NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN reanalysis_target_version  INTEGER,
    ADD COLUMN reanalysis_claimed_at      TIMESTAMP;

-- Every row that existed before this migration predates both concepts: it is exactly what the
-- DEFAULTs above backfill to - version 1, origin LEGACY - with no exception. New rows must
-- specify both explicitly (the claim/complete/persist paths always do), so the defaults are
-- dropped immediately rather than left as implicit defaults for future inserts.
ALTER TABLE job_analysis
    ALTER COLUMN analysis_version DROP DEFAULT,
    ALTER COLUMN analysis_origin DROP DEFAULT;

ALTER TABLE job_analysis
    ADD CONSTRAINT chk_job_analysis_version_positive CHECK (analysis_version > 0),
    ADD CONSTRAINT chk_job_analysis_origin CHECK (analysis_origin IN ('MANUAL', 'MONITORING', 'LEGACY')),
    ADD CONSTRAINT chk_job_analysis_reanalysis_target_version_positive
        CHECK (reanalysis_target_version IS NULL OR reanalysis_target_version > 0);

-- Supports the stale-reanalysis-claim lookup, the same way idx_job_analysis_status_updated_at
-- (V8) supports the stale first-analysis claim lookup: a reanalysis claim whose claimed_at is
-- older than the configured stale threshold is eligible to be reclaimed by the next caller
-- instead of blocking forever behind an abandoned attempt.
CREATE INDEX idx_job_analysis_reanalysis_claimed_at
    ON job_analysis (reanalysis_claimed_at)
    WHERE reanalysis_target_version IS NOT NULL;
