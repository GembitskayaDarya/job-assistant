package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.UUID;

/**
 * Sprint 11 Step 1: one achievement bullet, shared shape for both position-level and project-level
 * achievements - the complete-graph, no-AI counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerAchievement}. {@link #careerAchievementId}
 * is preserved so a future tailoring step can reference this exact source bullet rather than
 * asking the AI to recreate it by text.
 */
public record CvSourceAchievement(UUID careerAchievementId, String text) {

    public CvSourceAchievement {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("CV source achievement text must not be blank");
        }
    }
}
