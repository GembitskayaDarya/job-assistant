package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 2: one company selected for application-material generation - the bounded,
 * provenance-carrying counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerCompany}. {@link #careerCompanyId} is the
 * lightweight provenance reference back to that source row.
 *
 * <p>{@link #positions} holds only the positions at this company that {@code
 * CandidateContextForApplicationMaterialsSelector} actually selected, ordered by their own
 * original authored order (never re-sorted by relevance) - see that class's javadoc for why a CV
 * must not present one employer's positions out of chronological order.
 */
public record SelectedCareerCompany(
        UUID careerCompanyId,
        String name,
        String website,
        String industry,
        String location,
        String description,
        List<SelectedCareerPosition> positions
) {

    public SelectedCareerCompany {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Selected career company name must not be blank");
        }
        positions = positions == null ? List.of() : List.copyOf(positions);
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Selected career company '" + name + "' must have at least one selected position");
        }
    }
}
