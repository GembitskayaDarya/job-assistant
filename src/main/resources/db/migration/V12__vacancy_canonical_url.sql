-- Sprint 8 Step 4B1: canonical vacancy URL, used to deduplicate vacancies whose raw source URLs
-- differ only by tracking parameters, scheme/host case, a default port, a fragment, or a non-root
-- trailing slash but represent the same posting - see VacancyUrlCanonicalizer for the algorithm.
--
-- Nullable: existing rows have no canonical URL yet, and a full historical backfill is explicitly
-- deferred to Step 4B2, not attempted here. New rows populate this column at creation time via
-- VacancyCreationService.
ALTER TABLE vacancy
    ADD COLUMN canonical_url VARCHAR(1000);

-- Partial unique index (mirrors uk_vacancy_url): protects only non-null canonical identities, so
-- legacy rows can keep canonical_url = null without violating uniqueness, while the database
-- itself rejects two new rows created concurrently with the same canonical URL.
CREATE UNIQUE INDEX uk_vacancy_canonical_url ON vacancy (canonical_url) WHERE canonical_url IS NOT NULL;
