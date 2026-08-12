package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 2: the tailoring decisions for one existing career project, referenced by its
 * stable source {@code careerProjectId} - never a recreated company/position/project name, date,
 * or a fictional new project. A {@link CvTailoringResult} may carry zero or more of these, one per
 * project it chooses to tailor. See {@link CvPositionTailoring} (Step 2 correction) for the
 * equivalent contract over a position's own (non-project) responsibilities/achievements - the two
 * are deliberately separate types, not a shared generic "career node," so the allowed AI surface
 * for each stays easy to understand and validate independently.
 *
 * <p>{@link #responsibilities} and {@link #achievements} each express selection and presentation
 * order purely through list position - no id may appear twice within either list (an AI must not
 * select the same source bullet twice or submit two conflicting rewrites for it). The two lists are
 * independent id spaces (backed by different source rows) and are intentionally never cross-checked
 * against each other.
 *
 * <p>Project technologies are deliberately absent from this contract - they remain factual data
 * owned by {@code CvSourceSnapshot}; this step does not allow the AI to add, remove, or rewrite
 * them.
 *
 * <p>Whether {@link #careerProjectId} actually exists in a particular {@code CvSourceSnapshot} is
 * intentionally NOT checked here - that source-aware validation belongs to Sprint 11 Step 3's
 * guardrails, not this structural contract.
 */
public record CvProjectTailoring(
        UUID careerProjectId,
        List<CvResponsibilityTailoring> responsibilities,
        List<CvAchievementTailoring> achievements
) {

    public CvProjectTailoring {
        if (careerProjectId == null) {
            throw new IllegalArgumentException("Project tailoring careerProjectId must not be null");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        CvTailoringValidation.requireNoDuplicateResponsibilityIds(responsibilities, "project tailoring");
        CvTailoringValidation.requireNoDuplicateAchievementIds(achievements, "project tailoring");
    }
}
