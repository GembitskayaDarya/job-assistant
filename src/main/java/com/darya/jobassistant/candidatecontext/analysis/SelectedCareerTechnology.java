package com.darya.jobassistant.candidatecontext.analysis;

/**
 * Sprint 9 Step 8: one technology tag selected for a prompt, under a {@link SelectedCareerProject}.
 * {@link #name} is never lowercased or otherwise normalized for display, matching {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerTechnology}'s convention.
 */
public record SelectedCareerTechnology(String name, String category) {

    public SelectedCareerTechnology {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Selected career technology name must not be blank");
        }
    }
}
