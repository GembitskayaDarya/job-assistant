package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 2: one project selected for application-material generation, within a {@link
 * SelectedCareerPosition} - the bounded, provenance-carrying counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerProject}. {@link #careerProjectId} is the
 * lightweight provenance reference back to that source row. {@link #responsibilities}, {@link
 * #achievements}, and {@link #technologies} are already capped by {@code
 * CandidateContextForApplicationMaterialsProperties} before this record is constructed.
 */
public record SelectedCareerProject(
        UUID careerProjectId,
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
