-- Step 6: replaces the single ambiguous missing_skills list with separate required/preferred
-- gap lists, plus standalone experience/preferences assessment text. Additive and
-- backward-compatible: no existing row is deleted, truncated, or has its missing_skills value
-- discarded.
ALTER TABLE job_analysis
    ADD COLUMN missing_required_skills  TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN missing_preferred_skills TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN experience_assessment    TEXT,
    ADD COLUMN preferences_assessment   TEXT;

-- One-time backfill for rows that existed before this migration. The pre-Step-6 prompt produced
-- a single flat missing_skills list that never reliably distinguished a vacancy's required
-- requirements from its preferred/optional ones - treating that data as legacy required-gap data
-- is the closest safe compatibility mapping, but its original semantics were broader than
-- "clearly required and absent from the candidate profile". missing_preferred_skills is left
-- empty for these rows: there is no reliable way to retroactively split the old list.
UPDATE job_analysis
SET missing_required_skills = missing_skills
WHERE status = 'COMPLETED';

-- Legacy completed rows predate experience/preferences assessments entirely - give them an
-- explicit, non-blank fallback rather than leaving these columns null, since the domain model
-- requires both to be non-blank once mapped for a completed analysis.
UPDATE job_analysis
SET experience_assessment = 'Not assessed in the legacy analysis.',
    preferences_assessment = 'Not assessed in the legacy analysis.'
WHERE status = 'COMPLETED';

-- missing_skills itself is intentionally retained, not dropped or truncated: it is the only
-- historical record of the pre-Step-6 flat requirement list, and dropping it here would not be
-- safely reversible. The application no longer reads or writes this column going forward.
