package com.darya.jobassistant.applicationmaterials.render.model;

import java.time.LocalDate;

/**
 * Sprint 10 Step 4: canonical, presentation-ready mention of one selected project under a {@link
 * RenderableCvExperience} - name and dates only, resolved by {@code RenderModelAssembler} from the
 * exact {@code SelectedCareerProject} the generation's context selected. Never AI-supplied.
 */
public record RenderableCvProject(String name, LocalDate startDate, LocalDate endDate) {

    public RenderableCvProject {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Renderable CV project name must not be blank");
        }
    }
}
