-- Sprint 11 Step 5: additive persistence for Education - Candidate Profile-owned, Tier-1 flat
-- factual data (no nested children, unlike Career History/Personal Projects), matching
-- candidate_profile_skill/_language/_preference's existing shape: unidirectional FK to
-- candidate_profile, ON DELETE CASCADE, versioned together with the whole Candidate Profile save
-- (see CandidateProfileRepositoryAdapter). Fixed factual content - never AI-generated or
-- AI-tailored.
--
-- Only institution is mandatory: the factual import source may only provide
-- university/faculty information without a formal degree title, and the import process must
-- never invent one to satisfy a NOT NULL constraint.
CREATE TABLE candidate_profile_education (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_profile_id   UUID          NOT NULL REFERENCES candidate_profile (id) ON DELETE CASCADE,
    institution            VARCHAR(255)  NOT NULL,
    degree                 VARCHAR(255),
    field_of_study         VARCHAR(255),
    location               VARCHAR(300),
    start_date             DATE,
    end_date               DATE,
    description            TEXT,
    display_order          INTEGER       NOT NULL,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_candidate_profile_education_institution_not_blank
        CHECK (length(btrim(institution)) > 0),

    CONSTRAINT chk_candidate_profile_education_degree_not_blank
        CHECK (degree IS NULL OR length(btrim(degree)) > 0),

    CONSTRAINT chk_candidate_profile_education_field_of_study_not_blank
        CHECK (field_of_study IS NULL OR length(btrim(field_of_study)) > 0),

    CONSTRAINT chk_candidate_profile_education_display_order_non_negative
        CHECK (display_order >= 0),

    CONSTRAINT chk_candidate_profile_education_end_date_after_start_date
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),

    -- No uniqueness on institution/degree - the same institution can legitimately appear twice
    -- (e.g. a Bachelor's then a Master's at the same university).
    CONSTRAINT uk_candidate_profile_education_profile_id_display_order
        UNIQUE (candidate_profile_id, display_order)
);

CREATE INDEX idx_candidate_profile_education_candidate_profile_id
    ON candidate_profile_education (candidate_profile_id);
