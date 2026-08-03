package com.darya.jobassistant.candidates.aggregate;

/**
 * Which weighted/repeatable {@code CandidatePreferences} concept a {@link CandidateProfilePreference}
 * row represents - see V17's {@code candidate_profile_preference.preference_type} CHECK constraint,
 * which mirrors these four values exactly.
 */
public enum CandidatePreferenceType {

    /** At most one row per profile - {@code CandidatePreferences.preferredWorkArrangement}. */
    WORK_ARRANGEMENT,

    /**
     * Zero or more rows, always {@code importance == null} - {@code
     * CandidatePreferences.allowedWorkCountries}. Not order-significant: {@code
     * JobSearchQueryPlanner}'s class javadoc states this list is deliberately not used as search
     * geography at all, and {@code JobSearchQueryPlannerTest
     * .multipleAllowedWorkCountries_doNotAffectOutput_regardlessOfOrder} proves reordering it
     * produces identical planner output. No other production code reads it positionally - {@code
     * JobAnalysisService} only ever joins it into a flat, order-irrelevant prompt string.
     */
    ALLOWED_WORK_COUNTRY,

    /**
     * Zero or more rows sharing one importance - {@code CandidatePreferences.preferredContractTypes}.
     * Order-significant (see {@link #isOrderSignificant()}): {@code
     * JobSearchQueryPlanner#resolveContractTerm} walks this list and returns the first
     * non-blank/non-meaningless entry, so which contract type ends up embedded in the generated
     * search query genuinely depends on list position, not just membership.
     */
    CONTRACT_TYPE,

    /** At most one row per profile - {@code CandidatePreferences.preferredCompanyType}. */
    COMPANY_TYPE;

    /**
     * Whether this preference type's row order carries business meaning and must therefore be
     * persisted ({@code priority_order}) and compared/fingerprinted positionally rather than as a
     * set - see the per-constant javadoc above for the concrete evidence behind each type's
     * classification. {@code WORK_ARRANGEMENT}/{@code COMPANY_TYPE} are single-valued (at most one
     * row per profile - see {@link CandidateProfileAggregate}), so order never applies to them
     * either way.
     */
    public boolean isOrderSignificant() {
        return this == CONTRACT_TYPE;
    }
}
