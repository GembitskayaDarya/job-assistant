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
 * <p>{@link #orderedTechnologyIds} (Sprint 11 Step 6) references {@code
 * CvSourceTechnology#careerTechnologyId()} - list position expresses both which of this exact
 * project's factual technologies are shown and in what order, the same "selection and order via
 * list position alone" contract {@link CvTailoringResult#orderedSkillIds()} already uses. The AI can
 * never invent a free-form technology name this way, add a technology the project does not actually
 * have, or edit a technology's name/category - only choose which of the project's own factual
 * technologies to feature and in what order.
 *
 * <p>Whether {@link #careerProjectId} actually exists in a particular {@code CvSourceSnapshot}, and
 * whether every {@link #orderedTechnologyIds} entry actually belongs to that exact project, is
 * intentionally NOT checked here - that source-aware validation belongs to Sprint 11 Step 3/6's
 * guardrails, not this structural contract.
 */
public record CvProjectTailoring(
        UUID careerProjectId,
        List<CvResponsibilityTailoring> responsibilities,
        List<CvAchievementTailoring> achievements,
        List<UUID> orderedTechnologyIds
) {

    public CvProjectTailoring {
        if (careerProjectId == null) {
            throw new IllegalArgumentException("Project tailoring careerProjectId must not be null");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        CvTailoringValidation.requireNoDuplicateResponsibilityIds(responsibilities, "project tailoring");
        CvTailoringValidation.requireNoDuplicateAchievementIds(achievements, "project tailoring");
        // Validated on the raw, caller-supplied list before defensively copying - see
        // CvTailoringResult#orderedSkillIds's compact constructor for why this ordering matters.
        CvTailoringValidation.requireNoNullOrDuplicateIds(
                orderedTechnologyIds == null ? List.of() : orderedTechnologyIds, "project tailoring orderedTechnologyIds");
        orderedTechnologyIds = orderedTechnologyIds == null ? List.of() : List.copyOf(orderedTechnologyIds);
    }

    /**
     * Convenience constructor matching this type's pre-Sprint-11-Step-6 shape - defaults {@link
     * #orderedTechnologyIds} to empty, so the many existing call sites built before project
     * technology tailoring existed keep compiling unchanged.
     */
    public CvProjectTailoring(
            UUID careerProjectId, List<CvResponsibilityTailoring> responsibilities, List<CvAchievementTailoring> achievements) {
        this(careerProjectId, responsibilities, achievements, List.of());
    }
}
