package com.darya.jobassistant.candidates;

/**
 * How much a candidate work preference (contract type, company type, work arrangement, ...)
 * should weigh in vacancy analysis. This is a property of the candidate's preference, not a
 * vacancy match outcome - the persisted and displayed analysis result stays a numeric 0-100
 * score, never a category derived from this enum.
 */
public enum PreferenceImportance {

    REQUIRED,
    STRONG,
    PREFERRED,
    NEUTRAL
}
