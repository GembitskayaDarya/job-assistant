package com.darya.jobassistant.candidates.aggregate;

import com.darya.jobassistant.candidates.PreferenceImportance;

/**
 * One weighted/repeatable preference row within a {@link CandidateProfileAggregate} - see V17's
 * {@code candidate_profile_preference} table. {@code importance} is {@code null} exactly when
 * {@code type} is {@link CandidatePreferenceType#ALLOWED_WORK_COUNTRY} (which has no importance
 * dimension in the source YAML model at all) and required for every other type - mirrored exactly
 * by {@code chk_candidate_profile_preference_importance_matches_type}.
 *
 * <p>{@code priorityOrder} (added V18) is the row's zero-based position within its source list -
 * required exactly when {@link CandidatePreferenceType#isOrderSignificant()} is {@code true} for
 * {@code type} (currently only {@link CandidatePreferenceType#CONTRACT_TYPE}) and forbidden
 * otherwise, mirrored by {@code chk_candidate_profile_preference_priority_matches_type}. This
 * mirrors the {@code importance} nullability pattern above, but on the orthogonal
 * order-significance dimension rather than the weighting dimension.
 */
public record CandidateProfilePreference(
        CandidatePreferenceType type,
        String value,
        PreferenceImportance importance,
        Integer priorityOrder
) {
    public CandidateProfilePreference {
        if (type == null) {
            throw new IllegalArgumentException("Candidate profile preference type must not be null");
        }
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile preference value must not be null or blank");
        }
        boolean importanceExpected = type != CandidatePreferenceType.ALLOWED_WORK_COUNTRY;
        if (importanceExpected && importance == null) {
            throw new IllegalArgumentException("Candidate profile preference of type " + type + " requires an importance");
        }
        if (!importanceExpected && importance != null) {
            throw new IllegalArgumentException(
                    "Candidate profile preference of type " + type + " must not carry an importance");
        }
        if (type.isOrderSignificant() && priorityOrder == null) {
            throw new IllegalArgumentException("Candidate profile preference of type " + type + " requires a priorityOrder");
        }
        if (!type.isOrderSignificant() && priorityOrder != null) {
            throw new IllegalArgumentException(
                    "Candidate profile preference of type " + type + " must not carry a priorityOrder");
        }
        if (priorityOrder != null && priorityOrder < 0) {
            throw new IllegalArgumentException("Candidate profile preference priorityOrder must not be negative");
        }
    }

    /** Convenience constructor for the common case of an order-insignificant preference. */
    public CandidateProfilePreference(CandidatePreferenceType type, String value, PreferenceImportance importance) {
        this(type, value, importance, null);
    }
}
