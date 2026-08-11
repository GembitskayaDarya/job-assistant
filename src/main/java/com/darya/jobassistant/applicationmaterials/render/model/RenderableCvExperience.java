package com.darya.jobassistant.applicationmaterials.render.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 10 Step 4: one tailored experience entry ready to render - the canonical counterpart of
 * {@code GeneratedCvExperience}, which only ever carries a {@code careerPositionId}. Every field
 * here except {@link #bullets} is resolved by {@code RenderModelAssembler} directly from the
 * position that id refers to in the exact {@code CandidateContextForApplicationMaterials} used for
 * this generation - never from the AI response. This is precisely what prevents the AI from
 * substituting a different company name, position title, or date range: those fields simply do not
 * exist anywhere in the AI-facing schema for it to set.
 */
public record RenderableCvExperience(
        String companyName,
        String positionTitle,
        String employmentType,
        String location,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        List<String> bullets,
        List<RenderableCvProject> projects
) {

    public RenderableCvExperience {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Renderable CV experience companyName must not be blank");
        }
        if (positionTitle == null || positionTitle.isBlank()) {
            throw new IllegalArgumentException("Renderable CV experience positionTitle must not be blank");
        }
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
