package com.darya.jobassistant.candidates.migration;

import java.util.List;

/**
 * The result of {@link CandidateProfileSemanticComparator#diff}. {@code changedFields} carries
 * only safe field/category names (e.g. {@code "targetRole"}, {@code "skills"}) for logging - never
 * the actual differing values, which may be personal career data.
 */
public record CandidateProfileDiff(
        boolean equal,
        List<String> changedFields
) {
    public CandidateProfileDiff {
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }
}
