-- Sprint 11 Step 5 acceptance correction: candidate full name - fixed factual CV-presentation
-- data (professional-v1 requires it), the same scalar-fact pattern V26 already used for email/
-- phone/linkedin_url/cv_location/cv_headline. Additive: nullable for migration compatibility,
-- never read for AI matching, never AI-generated.
ALTER TABLE candidate_profile
    ADD COLUMN full_name VARCHAR(255);

ALTER TABLE candidate_profile
    ADD CONSTRAINT chk_candidate_profile_full_name_not_blank
        CHECK (full_name IS NULL OR length(btrim(full_name)) > 0);
