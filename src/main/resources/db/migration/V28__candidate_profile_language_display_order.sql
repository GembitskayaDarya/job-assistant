-- Sprint 11 Step 5: candidate_profile_language had no display_order at all - a CV language block
-- cannot be rendered deterministically without one (every other multi-entry list in this schema
-- already has one). Added in migration-safe steps: nullable column first, deterministic backfill,
-- then NOT NULL + uniqueness - never left nullable at the end of this migration.
--
-- The backfill order only needs to be deterministic (existing rows get *some* stable order); the
-- actual desired presentation order is a later, explicit private-candidate-data-import concern,
-- not something this migration invents.
ALTER TABLE candidate_profile_language
    ADD COLUMN display_order INTEGER;

WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY candidate_profile_id ORDER BY created_at ASC, id ASC
    ) - 1 AS computed_order
    FROM candidate_profile_language
)
UPDATE candidate_profile_language AS language
SET display_order = ordered.computed_order
FROM ordered
WHERE language.id = ordered.id;

ALTER TABLE candidate_profile_language
    ALTER COLUMN display_order SET NOT NULL;

ALTER TABLE candidate_profile_language
    ADD CONSTRAINT chk_candidate_profile_language_display_order_non_negative
        CHECK (display_order >= 0),

    ADD CONSTRAINT uk_candidate_profile_language_profile_id_display_order
        UNIQUE (candidate_profile_id, display_order);
