package com.darya.jobassistant.candidatecontext.cv.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 1: one project within a {@link CvSourcePosition} - the complete-graph, no-AI
 * counterpart of {@code com.darya.jobassistant.careerhistory.aggregate.CareerProject}. {@link
 * #careerProjectId} is preserved so a future tailoring step can reference this exact source project
 * rather than asking the AI to recreate it by text.
 *
 * <p>Unlike {@code candidatecontext.applicationmaterials.model.SelectedCareerProject}, {@link
 * #responsibilities}, {@link #achievements}, and {@link #technologies} are never capped or
 * character-truncated - this type carries every bullet and technology the source project has, in
 * its existing display order, because CV generation needs the complete factual universe to tailor
 * from, not a vacancy-bounded excerpt.
 */
public record CvSourceProject(
        UUID careerProjectId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<CvSourceResponsibility> responsibilities,
        List<CvSourceAchievement> achievements,
        List<CvSourceTechnology> technologies
) {

    public CvSourceProject {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CV source project name must not be blank");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
