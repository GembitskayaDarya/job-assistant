package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.UUID;

/**
 * Sprint 11 Step 5: one technology tag under a {@link CvSourcePersonalProject} - the
 * complete-graph, no-AI counterpart of {@code
 * personalprojects.aggregate.PersonalProjectTechnology}. {@link #personalProjectTechnologyId} is
 * preserved so a future tailoring step can reference this exact source row rather than asking the
 * AI to recreate it by text.
 */
public record CvSourcePersonalProjectTechnology(UUID personalProjectTechnologyId, String name, String category) {

    public CvSourcePersonalProjectTechnology {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CV source personal project technology name must not be blank");
        }
    }
}
