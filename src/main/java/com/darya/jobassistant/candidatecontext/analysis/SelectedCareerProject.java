package com.darya.jobassistant.candidatecontext.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 9 Step 8: one project selected for a prompt, within a {@link SelectedCareerPosition} -
 * the bounded counterpart of {@code com.darya.jobassistant.careerhistory.aggregate.CareerProject}.
 * Carries no persistence id and no unbounded child list: {@link #responsibilities}, {@link
 * #achievements}, and {@link #technologies} are already capped by {@link
 * CandidateContextAnalysisProperties} before this record is constructed.
 */
public record SelectedCareerProject(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<SelectedCareerResponsibility> responsibilities,
        List<SelectedCareerAchievement> achievements,
        List<SelectedCareerTechnology> technologies
) {

    public SelectedCareerProject {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Selected career project name must not be blank");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
