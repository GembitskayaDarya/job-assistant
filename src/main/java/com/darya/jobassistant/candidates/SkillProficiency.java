package com.darya.jobassistant.candidates;

/**
 * How confidently the candidate can use a given skill. Deliberately has no separate
 * experience-type dimension (e.g. commercial vs. pet-project) - proficiency alone is the model.
 *
 * <p>A skill absent from {@code CandidateProfile.skills} means the candidate does not know it,
 * has only negligible exposure, or intentionally excluded it as irrelevant - absence is never
 * modeled as an explicit skill entry, so there is no "not known" level here.
 */
public enum SkillProficiency {

    /** Understands the fundamentals and may need guidance for non-trivial work. */
    BASIC,

    /** Can independently complete normal implementation tasks. */
    WORKING,

    /** Has confident production experience and can troubleshoot difficult problems. */
    STRONG,

    /** Has deep knowledge, understands trade-offs and can design or guide others. */
    EXPERT
}
