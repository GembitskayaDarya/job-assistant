package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.util.UUID;

/**
 * Sprint 10 Step 2: one responsibility bullet selected for application-material generation -
 * factual source material, never AI-generated wording. Unlike {@code
 * candidatecontext.analysis.SelectedCareerResponsibility}, {@link #careerResponsibilityId} is
 * preserved as a lightweight provenance reference back to the source {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility} - so a future AI generation
 * step, and any anti-hallucination check built on top of it, can distinguish a candidate fact from
 * generated text without re-deriving it from free text. {@link #text} may carry the {@code
 * [truncated]} marker {@link
 * com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector}
 * appends when the source text exceeded the configured field-character bound.
 */
public record SelectedCareerResponsibility(UUID careerResponsibilityId, String text) {

    public SelectedCareerResponsibility {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Selected career responsibility text must not be blank");
        }
    }
}
