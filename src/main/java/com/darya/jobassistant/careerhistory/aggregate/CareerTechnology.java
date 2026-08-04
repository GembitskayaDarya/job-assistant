package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Sprint 9 Step 6: one ordered technology tag under a {@link CareerProject} (V19's {@code
 * career_project_technology}) - framework-free, immutable. {@link #name} is never lowercased or
 * otherwise normalized - "Java", "PostgreSQL", "AWS" keep their meaningful capitalization,
 * matching {@code CareerProjectTechnologyEntity}'s convention. No global technology dictionary is
 * introduced here.
 */
public record CareerTechnology(UUID id, String name, String category, int displayOrder) {

    public CareerTechnology {
        name = CareerHistoryValidation.requireNonBlank(name, "Technology name must not be blank");
        category = CareerHistoryValidation.blankToNull(category);
        CareerHistoryValidation.requireNonNegative(displayOrder, "Technology display order must not be negative");
    }
}
