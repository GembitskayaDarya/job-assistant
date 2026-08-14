-- Sprint 11 Step 5: additive CV header/contact facts on candidate_profile - email, phone,
-- LinkedIn URL, CV location, and a fixed factual CV headline. All nullable: this migration only
-- adds columns, it never seeds real personal data (that happens through the existing private-YAML
-- import path, never hardcoded here). Nothing reads these columns yet.
--
-- cv_headline is deliberately separate from the existing target_role column: target_role is an
-- operational job-search/vacancy-matching input (fed into the AI analysis prompt and into
-- JobSearchQueryPlanner's outbound search queries), while cv_headline is the fixed, never-AI-
-- generated title printed on the CV document itself - the two must be free to diverge (evidenced
-- today by the private profile's target_role text differing from the approved CV headline text).
ALTER TABLE candidate_profile
    ADD COLUMN email        VARCHAR(255),
    ADD COLUMN phone        VARCHAR(50),
    ADD COLUMN linkedin_url VARCHAR(500),
    ADD COLUMN cv_location  VARCHAR(300),
    ADD COLUMN cv_headline  VARCHAR(255);

ALTER TABLE candidate_profile
    ADD CONSTRAINT chk_candidate_profile_email_format
        CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),

    ADD CONSTRAINT chk_candidate_profile_phone_not_blank
        CHECK (phone IS NULL OR length(btrim(phone)) > 0),

    -- Allows the plain domain and any subdomain (e.g. www.linkedin.com, pl.linkedin.com).
    ADD CONSTRAINT chk_candidate_profile_linkedin_url_format
        CHECK (linkedin_url IS NULL OR linkedin_url ~* '^https://([a-z0-9-]+\.)*linkedin\.com/.+'),

    ADD CONSTRAINT chk_candidate_profile_cv_location_not_blank
        CHECK (cv_location IS NULL OR length(btrim(cv_location)) > 0),

    ADD CONSTRAINT chk_candidate_profile_cv_headline_not_blank
        CHECK (cv_headline IS NULL OR length(btrim(cv_headline)) > 0);
