package com.darya.jobassistant.candidatecontext.applicationmaterials.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 2: one position selected for application-material generation - the bounded,
 * provenance-carrying counterpart of {@code
 * com.darya.jobassistant.careerhistory.aggregate.CareerPosition}. {@link #careerPositionId} is the
 * lightweight provenance reference back to that source row.
 *
 * <p>Unlike {@code candidatecontext.analysis.SelectedCareerPosition}, company identity is not
 * folded into this record as a flat string - {@link SelectedCareerCompany} keeps the natural
 * company/position nesting from {@code CareerHistoryAggregate} intact, since a tailored CV/cover
 * letter (unlike a one-shot analysis prompt) needs to group positions under their employer.
 *
 * <p>{@link #projects} is already limited to the top-ranked, still-fitting-in-budget projects
 * selected for this position - never every project the position actually has.
 */
public record SelectedCareerPosition(
        UUID careerPositionId,
        String title,
        String employmentType,
        String location,
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
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
