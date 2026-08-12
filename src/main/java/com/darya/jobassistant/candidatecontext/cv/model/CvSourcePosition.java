package com.darya.jobassistant.candidatecontext.cv.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 1: one position within a {@link CvSourceCompany} - the complete-graph, no-AI
 * counterpart of {@code com.darya.jobassistant.careerhistory.aggregate.CareerPosition}. {@link
 * #careerPositionId} is preserved so a future tailoring step can reference this exact source
 * position rather than asking the AI to recreate it by text.
 *
 * <p>Unlike {@code candidatecontext.applicationmaterials.model.SelectedCareerPosition}, {@link
 * #projects} holds every project the position actually has, not just the top-ranked ones still
 * fitting a vacancy character budget - CV generation needs the complete factual universe to tailor
 * from.
 */
public record CvSourcePosition(
        UUID careerPositionId,
        String title,
        String employmentType,
        String location,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        String description,
        List<CvSourceResponsibility> responsibilities,
        List<CvSourceAchievement> achievements,
        List<CvSourceProject> projects
) {

    public CvSourcePosition {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("CV source position title must not be blank");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
