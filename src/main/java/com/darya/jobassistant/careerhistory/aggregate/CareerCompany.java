package com.darya.jobassistant.careerhistory.aggregate;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 9 Step 6: one company within a {@link CareerHistoryAggregate} (V19/V20's {@code
 * career_company} plus its position children) - framework-free, immutable.
 *
 * <p>{@link #displayOrder} is explicit business ordering data (V20) - never derived from {@link
 * #name} or any child position's dates. Position display orders are unique within one company;
 * position names are deliberately not required to be unique here (no such database constraint
 * exists, and two positions at the same company can legitimately share a title).
 */
public record CareerCompany(
        UUID id,
        String name,
        String website,
        String industry,
        String location,
        String description,
        int displayOrder,
        List<CareerPosition> positions
) {

    public CareerCompany {
        name = CareerHistoryValidation.requireNonBlank(name, "Company name must not be blank");
        website = CareerHistoryValidation.blankToNull(website);
        industry = CareerHistoryValidation.blankToNull(industry);
        location = CareerHistoryValidation.blankToNull(location);
        description = CareerHistoryValidation.blankToNull(description);
        CareerHistoryValidation.requireNonNegative(displayOrder, "Company display order must not be negative");

        positions = CareerHistoryValidation.copySortedByDisplayOrder(positions, CareerPosition::displayOrder);
        CareerHistoryValidation.requireUniqueDisplayOrders(positions, CareerPosition::displayOrder, "Duplicate position display order");
    }
}
