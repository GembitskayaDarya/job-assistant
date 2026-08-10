package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.util.UUID;

/**
 * Sprint 10 Step 2: one achievement bullet selected for application-material generation - see
 * {@link SelectedCareerResponsibility} for the shared provenance/truncation convention.
 * {@link #careerAchievementId} is the lightweight provenance reference back to the source {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerAchievement}.
 */
public record SelectedCareerAchievement(UUID careerAchievementId, String text) {

    public SelectedCareerAchievement {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Selected career achievement text must not be blank");
        }
    }
}
