package com.darya.jobassistant.applicationmaterials.generation.model;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 3: one AI-tailored bullet under a {@link GeneratedCvExperience}, carrying one or
 * more provenance references back to the exact Career History source nodes (a {@code
 * careerPositionId}, {@code careerProjectId}, {@code careerResponsibilityId}, {@code
 * careerAchievementId}, or {@code careerTechnologyId} - see {@code
 * candidatecontext.applicationmaterials.model.SelectedCareer*}) it was generated from.
 *
 * <p>{@link #sourceIds} is mandatory and non-empty: a bullet with no source reference has no
 * traceable factual basis and is rejected outright - see {@code
 * GeneratedApplicationMaterialsValidator}. A bullet may legitimately combine multiple source ids
 * when it safely merges facts from more than one bullet/technology/project.
 *
 * <p>This record itself only enforces its own shape (non-blank text, non-empty ids); whether each
 * id actually exists in the bounded context used for this generation, and whether it belongs to
 * the position this bullet is nested under, is the validator's job - it requires the exact
 * generation-scoped context to check against, which this framework-free record does not carry.
 */
public record GeneratedExperienceBullet(String text, List<UUID> sourceIds) {

    public GeneratedExperienceBullet {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Generated experience bullet text must not be blank");
        }
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        if (sourceIds.isEmpty()) {
            throw new IllegalArgumentException("Generated experience bullet must carry at least one source reference");
        }
    }
}
