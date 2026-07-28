-- Sprint 8 Step 4B2D: finalizes canonical_url as a mandatory, globally unique vacancy identity,
-- now that Step 4B2B's read-only audit + DRY_RUN + APPLY backfill has populated every legacy row
-- in this environment. This migration only ALTERs schema - it never writes, backfills, deletes,
-- or merges vacancy data itself.
--
-- Deployment precondition for any environment still on V12: before an application build
-- containing this migration is started against that database, an operator must run, in order -
-- 1) the read-only canonical URL audit (VacancyCanonicalUrlAuditRunner,
--    VACANCY_CANONICAL_URL_AUDIT_ENABLED=true) to confirm zero blockers;
-- 2) the backfill in DRY_RUN mode (VacancyCanonicalUrlBackfillRunner,
--    VACANCY_CANONICAL_URL_BACKFILL_ENABLED=true, VACANCY_CANONICAL_URL_BACKFILL_MODE=DRY_RUN) to
--    confirm the planned assignment count and zero blockers;
-- 3) the backfill in APPLY mode (same runner, VACANCY_CANONICAL_URL_BACKFILL_MODE=APPLY) to
--    atomically populate every legacy row;
-- 4) re-verify zero legacy rows remain (canonical_url IS NULL).
-- An environment that skips this sequence and starts an application containing V13 anyway will
-- have this migration's own preconditions below fail loudly and leave the schema exactly as V12
-- left it - it will not start with an invalid schema silently accepted.
--
-- The whole file runs as one Flyway-managed transaction (no CREATE INDEX CONCURRENTLY is used
-- anywhere here, deliberately - see the index-swap step), so a precondition failure, or any later
-- statement failure, rolls back everything below atomically; nothing partial is ever left behind,
-- and a corrected retry of this same migration version cannot collide with a leftover temporary
-- object from a prior failed attempt, since that attempt's CREATE INDEX was rolled back with it.

-- Preconditions: fail clearly, with counts but never full URLs, rather than silently repairing or
-- skipping bad data. This migration is schema-only - resolving any of these is the operator's job
-- (via the audit/backfill tools above), not this migration's.
DO $$
DECLARE
    null_count integer;
    blank_count integer;
    duplicate_value_count integer;
BEGIN
    SELECT COUNT(*) INTO null_count FROM vacancy WHERE canonical_url IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'V13 precondition failed: % vacancy row(s) still have canonical_url IS NULL - run the canonical URL audit and backfill (DRY_RUN then APPLY) against this database before deploying this migration', null_count;
    END IF;

    SELECT COUNT(*) INTO blank_count FROM vacancy WHERE canonical_url IS NOT NULL AND trim(canonical_url) = '';
    IF blank_count > 0 THEN
        RAISE EXCEPTION 'V13 precondition failed: % vacancy row(s) have a blank canonical_url (non-null but empty once trimmed)', blank_count;
    END IF;

    SELECT COUNT(*) INTO duplicate_value_count
    FROM (
        SELECT canonical_url
        FROM vacancy
        WHERE canonical_url IS NOT NULL
        GROUP BY canonical_url
        HAVING COUNT(*) > 1
    ) duplicated_canonical_urls;
    IF duplicate_value_count > 0 THEN
        RAISE EXCEPTION 'V13 precondition failed: % distinct canonical_url value(s) are shared by more than one vacancy row - resolve the collision before deploying this migration', duplicate_value_count;
    END IF;
END $$;

-- Every row is now confirmed non-null, non-blank, and unique - safe to enforce at the column level.
ALTER TABLE vacancy
    ALTER COLUMN canonical_url SET NOT NULL;

-- Atomic index swap: create the new full (non-partial) unique index under a temporary name first,
-- so the table is never left without unique protection between statements, then drop the old
-- partial index and rename the new one into its place. All three statements are part of this same
-- transaction, so no concurrent transaction can ever observe the table with zero, or with two
-- differently-scoped, unique indexes on canonical_url - it sees either the complete V12 state or
-- the complete V13 state, atomically.
CREATE UNIQUE INDEX uk_vacancy_canonical_url_full
    ON vacancy (canonical_url);

DROP INDEX uk_vacancy_canonical_url;

-- The application's conflict classification (VacancyCreationService.isCanonicalUrlConflict) keys
-- off this exact index name - renaming back to it, rather than leaving the new index under its
-- temporary name, is required, not cosmetic.
ALTER INDEX uk_vacancy_canonical_url_full
    RENAME TO uk_vacancy_canonical_url;
