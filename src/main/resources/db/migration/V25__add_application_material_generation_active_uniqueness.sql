-- Sprint 10 Step 5 (production-readiness acceptance fix): closes the concurrent "two simultaneous
-- first-time preparation requests" race PrepareApplicationPackageUseCase cannot close on its own -
-- two requests that each observe no matching generation for the same (vacancy, candidate context)
-- and then both insert a new PENDING row are different primary keys, so V21's ordinary optimistic
-- locking (which only protects updates to one already-existing row) never sees them collide.
--
-- PostgreSQL itself becomes the final authority: at most one ACTIVE (PENDING or IN_PROGRESS)
-- generation may exist per (vacancy_id, candidate_profile_version, career_history_version). This is
-- deliberately a partial index (WHERE status IN (...)), not a table-wide unique constraint -
-- COMPLETED and FAILED generations for the same effective key must remain unrestricted:
--   * a COMPLETED generation coexisting with the key is exactly what
--     PrepareApplicationPackageUseCase's reuse path expects to find and read;
--   * a FAILED generation must never block a later explicit new request from creating a fresh
--     PENDING row for the same key (see that use case's FAILED-handling rule);
--   * a future explicit "regenerate anyway" feature may legitimately create another COMPLETED
--     generation from the same candidate context - this index must never stand in its way.
--
-- NULLS NOT DISTINCT (PostgreSQL 15+; this project runs 16 in both production - see
-- docker-compose.yml - and every Testcontainers-backed test) makes two rows with the same
-- vacancy_id/candidate_profile_version and a NULL career_history_version collide exactly like any
-- other equal value would. Without it, ordinary unique-index semantics treat every NULL as
-- distinct from every other NULL, silently reopening this exact race whenever the candidate has no
-- Career History (CareerHistoryAvailability.NOT_PROVIDED) - i.e. career_history_version IS NULL is
-- itself a normal, common effective key, not an edge case to special-case away with a COALESCE
-- sentinel.
--
-- Constraint name uses the "amg" abbreviation (Application Material Generation), matching every
-- other constraint on this table (see V21) for the same "PostgreSQL silently truncates identifiers
-- over 63 bytes" reason.
CREATE UNIQUE INDEX uk_amg_active_effective_key
    ON application_material_generation (vacancy_id, candidate_profile_version, career_history_version)
    NULLS NOT DISTINCT
    WHERE status IN ('PENDING', 'IN_PROGRESS');
