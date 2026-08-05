package com.darya.jobassistant.candidatecontext.analysis;

/**
 * Sprint 9 Step 8: one achievement bullet selected for a prompt - see {@link
 * SelectedCareerResponsibility} for the shared truncation/no-persistence-id convention.
 */
public record SelectedCareerAchievement(String text) {

    public SelectedCareerAchievement {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Selected career achievement text must not be blank");
        }
    }
}
