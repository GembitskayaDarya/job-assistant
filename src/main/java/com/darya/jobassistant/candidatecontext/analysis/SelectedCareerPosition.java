package com.darya.jobassistant.candidatecontext.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 9 Step 8: one position selected for a prompt - the bounded counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerPosition}, with {@link #companyName}
 * folded in (flattened from the owning {@code CareerCompany}, since a prompt has no reason to
 * nest company underneath position the way persistence does). Carries no persistence id; {@link
 * #projects} is already limited to the globally top-ranked, still-fitting-in-budget projects
 * selected for this position - never every project the position actually has.
 */
public record SelectedCareerPosition(
        String title,
        String companyName,
        String employmentType,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        String description,
        List<SelectedCareerResponsibility> responsibilities,
        List<SelectedCareerAchievement> achievements,
        List<SelectedCareerProject> projects
) {

    public SelectedCareerPosition {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Selected career position title must not be blank");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Selected career position company name must not be blank");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
