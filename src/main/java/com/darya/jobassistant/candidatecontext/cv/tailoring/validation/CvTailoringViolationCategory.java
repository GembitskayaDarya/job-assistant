package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

/**
 * Sprint 11 Step 3: the reference-integrity/ownership problems {@link CvTailoringValidator} can
 * report for one {@code CvTailoringResult} relative to the {@code CvSourceSnapshot} it was
 * validated against. Deliberately does not distinguish "references something that belongs to a
 * different owner" from "references something that does not exist at all" within one hierarchy
 * level - both collapse into the same {@code *_NOT_OWNED} category, since the diagnostic value of
 * separating them is low and {@link CvTailoringViolation#parentId()}/{@link
 * CvTailoringViolation#referencedId()} already carry everything needed to look the mismatch up.
 */
public enum CvTailoringViolationCategory {

    /** An {@code orderedSkillIds} entry does not match any {@code candidateSkillId} in the snapshot. */
    UNKNOWN_SKILL_REFERENCE,

    /** A {@code CvPositionTailoring.careerPositionId} does not match any position in the snapshot. */
    UNKNOWN_POSITION_REFERENCE,

    /** A {@code CvProjectTailoring.careerProjectId} does not match any project in the snapshot. */
    UNKNOWN_PROJECT_REFERENCE,

    /**
     * A responsibility referenced through position tailoring is not among that exact position's own
     * position-level responsibilities - either it does not exist at all, belongs to a different
     * position, or belongs to a project instead.
     */
    POSITION_RESPONSIBILITY_NOT_OWNED,

    /** The achievement counterpart of {@link #POSITION_RESPONSIBILITY_NOT_OWNED}. */
    POSITION_ACHIEVEMENT_NOT_OWNED,

    /**
     * A responsibility referenced through project tailoring is not among that exact project's own
     * project-level responsibilities - either it does not exist at all, belongs to a different
     * project, or belongs to a position instead.
     */
    PROJECT_RESPONSIBILITY_NOT_OWNED,

    /** The achievement counterpart of {@link #PROJECT_RESPONSIBILITY_NOT_OWNED}. */
    PROJECT_ACHIEVEMENT_NOT_OWNED,

    /**
     * Sprint 11 Step 6: a technology referenced through {@code CvProjectTailoring.orderedTechnologyIds}
     * is not among that exact project's own factual technologies - either it does not exist at all or
     * belongs to a different project.
     */
    PROJECT_TECHNOLOGY_NOT_OWNED,

    /**
     * Sprint 11 Step 6: a {@code CvPersonalProjectTailoring.personalProjectId} does not match any
     * Personal Project in the snapshot.
     */
    UNKNOWN_PERSONAL_PROJECT_REFERENCE,

    /**
     * Sprint 11 Step 6: a highlight referenced through {@code
     * CvPersonalProjectTailoring.orderedHighlightIds} is not among that exact Personal Project's own
     * factual highlights - either it does not exist at all or belongs to a different Personal Project.
     */
    PERSONAL_PROJECT_HIGHLIGHT_NOT_OWNED,

    /**
     * Sprint 11 Step 6: a technology referenced through {@code
     * CvPersonalProjectTailoring.orderedTechnologyIds} is not among that exact Personal Project's own
     * factual technologies - either it does not exist at all or belongs to a different Personal
     * Project.
     */
    PERSONAL_PROJECT_TECHNOLOGY_NOT_OWNED
}
