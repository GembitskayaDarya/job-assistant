package com.darya.jobassistant.candidates;

/**
 * How confidently the candidate can use a given skill. Deliberately has no separate
 * experience-type dimension (e.g. commercial vs. pet-project) - proficiency alone is the model.
 */
public enum SkillProficiency {

    /** The candidate does not know or cannot currently use the skill. */
    NONE,

    /** Understands the fundamentals but has limited practical confidence. */
    BASIC,

    /** Can independently use the skill in regular development tasks. */
    WORKING,

    /** Confidently uses the skill and understands common production concerns. */
    STRONG,

    /** Has deep knowledge and can make design decisions, explain trade-offs and guide others. */
    EXPERT
}
