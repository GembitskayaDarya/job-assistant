package com.darya.jobassistant.personalprojects.aggregate;

import java.util.UUID;

/**
 * Sprint 11 Step 5: one factual technology tag within a {@link PersonalProject} (V29's {@code
 * personal_project_technology}) - fixed factual source data, never invented by AI. Unlike a
 * project's {@link PersonalProject#name()}, duplicate technology names within one project are not
 * meaningful - {@link PersonalProject}'s compact constructor rejects them using a normalized
 * (trimmed, case-insensitive) comparison, so {@code "Kafka"}/{@code "kafka"}/{@code " Kafka "}
 * cannot coexist as separate facts even though the database's own uniqueness constraint alone is
 * plain, case-sensitive equality.
 */
public record PersonalProjectTechnology(
        UUID id,
        String name,
        String category,
        int displayOrder
) {

    public PersonalProjectTechnology {
        name = PersonalProjectValidation.requireNonBlank(name, "Personal project technology name must not be blank");
        category = PersonalProjectValidation.blankToNull(category);
        PersonalProjectValidation.requireNonNegative(displayOrder, "Personal project technology display order must not be negative");
    }

    /** Convenience constructor for a technology with no persisted identity yet. */
    public PersonalProjectTechnology(String name, String category, int displayOrder) {
        this(null, name, category, displayOrder);
    }
}
