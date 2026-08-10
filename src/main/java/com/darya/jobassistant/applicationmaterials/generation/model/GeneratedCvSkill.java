package com.darya.jobassistant.applicationmaterials.generation.model;

import com.darya.jobassistant.candidates.SkillProficiency;

/**
 * Sprint 10 Step 3: one skill/technology surfaced in a {@link GeneratedCv}. {@link #proficiency}
 * is never trusted from the AI response - it is always resolved by {@code
 * GeneratedApplicationMaterialsValidator} by looking {@link #name} up against the candidate's own
 * {@code CandidateProfile.skills()}. When {@link #name} instead matches a Career-History-only
 * technology (candidate-owned but with no recorded proficiency anywhere in the source data),
 * {@link #proficiency} is {@code null} - never invented. A {@link #name} matching neither source is
 * rejected outright (a vacancy-only technology must never enter this list).
 *
 * <p>{@link #proficiency}, when present, is always one of the project's four supported levels
 * ({@link SkillProficiency#BASIC}/{@link SkillProficiency#WORKING}/{@link SkillProficiency#STRONG}/
 * {@link SkillProficiency#EXPERT}) - the enum itself has no {@code NONE} value to reintroduce.
 */
public record GeneratedCvSkill(String name, SkillProficiency proficiency) {

    public GeneratedCvSkill {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Generated CV skill name must not be blank");
        }
    }
}
