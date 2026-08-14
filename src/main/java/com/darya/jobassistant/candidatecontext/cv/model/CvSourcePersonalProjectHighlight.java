package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.UUID;

/**
 * Sprint 11 Step 5: one highlight bullet within a {@link CvSourcePersonalProject} - the
 * complete-graph, no-AI counterpart of {@code
 * personalprojects.aggregate.PersonalProjectHighlight}. {@link #personalProjectHighlightId} is
 * preserved so a future tailoring step can reference this exact source bullet rather than asking
 * the AI to recreate it by text.
 */
public record CvSourcePersonalProjectHighlight(UUID personalProjectHighlightId, String text) {

    public CvSourcePersonalProjectHighlight {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("CV source personal project highlight text must not be blank");
        }
    }
}
