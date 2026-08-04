package com.darya.jobassistant.careerhistory.aggregate;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 9 Step 6: framework-free domain aggregate for the complete Career History business
 * object (V19/V20's {@code career_history} plus its full company/position/project/bullet/
 * technology graph) - the type {@link CareerHistoryRepositoryPort} loads and saves as one unit.
 *
 * <p>A separate aggregate from {@code CandidateProfileAggregate}, associated with it only through
 * {@link #candidateProfileId} - mirrors that aggregate's shape (nullable {@link #id} for a
 * not-yet-persisted aggregate, non-negative {@link #version} as the optimistic-locking token the
 * caller must supply unchanged from a prior load to update that exact revision) without
 * mechanically copying it: unlike Candidate Profile, this aggregate's children are never persisted
 * or versioned independently of the whole graph, and a {@link CareerHistoryRepositoryPort#save}
 * always treats the supplied graph as the complete desired state (see that port's javadoc).
 *
 * <p>{@link #companies} may legitimately be empty - Career History's absence-and-completeness
 * policy belongs to the Step 7 import workflow, not this aggregate; a Candidate Profile with a
 * persisted-but-empty Career History is not itself invalid here.
 */
public record CareerHistoryAggregate(
        UUID id,
        UUID candidateProfileId,
        List<CareerCompany> companies,
        long version
) {

    public CareerHistoryAggregate {
        if (candidateProfileId == null) {
            throw new IllegalArgumentException("Career history candidate profile id must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Career history version must not be negative");
        }

        companies = CareerHistoryValidation.copySortedByDisplayOrder(companies, CareerCompany::displayOrder);
        CareerHistoryValidation.requireUniqueDisplayOrders(companies, CareerCompany::displayOrder, "Duplicate company display order");
        CareerHistoryValidation.requireUniqueBy(companies, CareerCompany::name, "Duplicate company name in career history");
    }
}
