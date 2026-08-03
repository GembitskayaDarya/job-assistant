-- Sprint 9 Step 5 correction: companies within a Career History need an explicit, stable order -
-- dates alone cannot serve this purpose (positions may overlap, dates may be incomplete, two
-- companies may share identical date boundaries, and a later import/export or AI/CV rendering
-- step needs a deterministic, explicitly-chosen order). V19 is treated as immutable (already
-- applied); this is purely additive.
--
-- No production or shared environment can already contain career_company rows - Career History
-- has no import/seed workflow yet (Step 6+) - but this migration is still written as if it might:
-- add the column nullable, backfill deterministic values per career_history_id, only then add
-- NOT NULL and the constraints. A migration that assumes "the table happens to be empty right
-- now" is not safe to reapply against any environment where that assumption silently stops being
-- true.
ALTER TABLE career_company ADD COLUMN display_order INTEGER;

-- Deterministic, collision-free backfill for any existing rows: number companies within each
-- Career History by primary-key insertion order. This is an arbitrary but valid order for a
-- schema migration to assign - a real, business-meaningful order is Step 6+ import-workflow data,
-- not something this migration can know.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY career_history_id ORDER BY id) - 1 AS rn
    FROM career_company
)
UPDATE career_company
SET display_order = ordered.rn
FROM ordered
WHERE career_company.id = ordered.id;

ALTER TABLE career_company ALTER COLUMN display_order SET NOT NULL;

ALTER TABLE career_company
    ADD CONSTRAINT chk_career_company_display_order_non_negative
        CHECK (display_order >= 0);

ALTER TABLE career_company
    ADD CONSTRAINT uk_career_company_history_id_display_order
        UNIQUE (career_history_id, display_order);
