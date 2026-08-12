package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.UUID;

/**
 * Sprint 11 Step 1: one technology tag under a {@link CvSourceProject} - the complete-graph, no-AI
 * counterpart of {@code com.darya.jobassistant.careerhistory.aggregate.CareerTechnology}. {@link
 * #careerTechnologyId} is preserved so a future tailoring step can reference this exact source row
 * rather than asking the AI to recreate it by text.
 */
public record CvSourceTechnology(UUID careerTechnologyId, String name, String category) {

    public CvSourceTechnology {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CV source technology name must not be blank");
        }
    }
}
