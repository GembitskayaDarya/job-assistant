package com.darya.jobassistant.candidatecontext.analysis;

/**
 * Sprint 9 Step 8: one responsibility bullet selected for a prompt, after relevance selection and
 * character-budget truncation - {@link #text} may carry the {@code [truncated]} marker {@link
 * CandidateContextForAnalysisSelector} appends when the source text exceeded {@link
 * CandidateContextAnalysisProperties#maxFieldCharacters()}. Carries no persistence id - never a
 * substitute for {@code com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility}.
 */
public record SelectedCareerResponsibility(String text) {

    public SelectedCareerResponsibility {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Selected career responsibility text must not be blank");
        }
    }
}
