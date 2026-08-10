package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.util.UUID;

/**
 * Sprint 10 Step 2: one technology tag selected for application-material generation, under a
 * {@link SelectedCareerProject}. {@link #careerTechnologyId} is the lightweight provenance
 * reference back to the source {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerTechnology}. {@link #name} is never
 * lowercased or otherwise normalized for display, matching that source type's convention.
 */
public record SelectedCareerTechnology(UUID careerTechnologyId, String name, String category) {

    public SelectedCareerTechnology {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Selected career technology name must not be blank");
        }
    }
}
