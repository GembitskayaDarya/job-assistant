package com.darya.jobassistant.candidatecontext;

/**
 * Sprint 9 Step 8: the three states {@link CandidateContextSnapshot#careerHistoryAvailability()}
 * can report - framework-free, no dependency on {@code CareerHistoryAggregate} beyond the
 * snapshot deriving it from one.
 */
public enum CareerHistoryAvailability {

    /** {@code CareerHistoryRepositoryPort#findByCandidateProfileId} returned {@code Optional.empty()}. */
    NOT_PROVIDED,

    /** A {@code CareerHistoryAggregate} exists but its {@code companies} list is empty. */
    EMPTY,

    /** A {@code CareerHistoryAggregate} exists with at least one company. */
    AVAILABLE
}
