package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.UUID;

/**
 * Sprint 11 Step 1: one responsibility bullet, shared shape for both position-level and
 * project-level responsibilities - the complete-graph, no-AI counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility}. {@link
 * #careerResponsibilityId} is preserved so a future tailoring step can reference this exact source
 * bullet rather than asking the AI to recreate it by text.
 */
public record CvSourceResponsibility(UUID careerResponsibilityId, String text) {

    public CvSourceResponsibility {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("CV source responsibility text must not be blank");
        }
    }
}
