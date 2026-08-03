-- Sprint 9 Step 3 correction: source list order is semantic for CONTRACT_TYPE preferences -
-- JobSearchQueryPlanner#resolveContractTerm picks the first non-blank/non-meaningless entry, so
-- which contract type is embedded in a generated search query genuinely depends on list position,
-- not just membership. ALLOWED_WORK_COUNTRY remains order-insensitive (JobSearchQueryPlanner's own
-- javadoc: deliberately not used as search geography; JobSearchQueryPlannerTest proves reordering
-- it is a no-op) and is not required to carry a priority_order. V17 is unchanged; this migration
-- only adds a new nullable/CHECK-constrained column and a new partial-scope unique constraint.

ALTER TABLE candidate_profile_preference
    ADD COLUMN priority_order INTEGER;

ALTER TABLE candidate_profile_preference
    ADD CONSTRAINT chk_candidate_profile_preference_priority_order_non_negative
        CHECK (priority_order IS NULL OR priority_order >= 0);

-- Ties priority_order nullability to preference_type exactly the same way
-- chk_candidate_profile_preference_importance_matches_type ties importance to preference_type -
-- only CONTRACT_TYPE (today's sole order-significant type) may carry a priority_order.
ALTER TABLE candidate_profile_preference
    ADD CONSTRAINT chk_candidate_profile_preference_priority_matches_type
        CHECK ((preference_type = 'CONTRACT_TYPE') = (priority_order IS NOT NULL));

-- Two rows of the same order-significant type cannot both claim the same position in the source
-- list. A plain UNIQUE constraint (rather than a partial index) is sufficient: standard SQL/
-- PostgreSQL treats every NULL priority_order as distinct from every other NULL, so the many rows
-- of non-order-significant types that always carry a NULL priority_order never collide here -
-- preserves the existing uk_candidate_profile_preference_profile_type_value from V17 unchanged.
ALTER TABLE candidate_profile_preference
    ADD CONSTRAINT uk_candidate_profile_preference_profile_type_priority
        UNIQUE (candidate_profile_id, preference_type, priority_order);
