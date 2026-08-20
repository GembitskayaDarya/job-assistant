package com.darya.jobassistant.applicationmaterials.render.ats;

/**
 * Sprint 11 Big Block 7: the structural/mechanical PDF-readability problems {@link AtsCvVerifier}
 * can report. This is not a predictive ATS score for any particular vendor - only whether the
 * rendered PDF's text is selectable/searchable and preserves the source {@code TailoredCvDocument}'s
 * factual structure and reading order.
 */
public enum AtsViolationCategory {

    /** The PDF produced no selectable/extractable text at all - the single most severe failure. */
    NO_SELECTABLE_TEXT,

    MISSING_FULL_NAME,
    MISSING_HEADLINE,
    MISSING_EMAIL,
    MISSING_PHONE,
    MISSING_LINKEDIN_TEXT,

    /** A mandatory section heading (for a section the document actually has content for) did not survive extraction. */
    MISSING_SECTION_HEADING,

    /** "Professional Experience" must extract before "Education" - both present, wrong order. */
    EXPERIENCE_NOT_BEFORE_EDUCATION,

    /** A company/position/project's name, date range, or bullet did not extract in the expected reading order. */
    ORDERING_VIOLATION,

    MISSING_PERSONAL_PROJECT_CONTENT,
    MISSING_EDUCATION_CONTENT,
    MISSING_LANGUAGE_CONTENT,

    /** A selected skill term did not survive extraction anywhere in the document. */
    MISSING_SKILL_TERM
}
