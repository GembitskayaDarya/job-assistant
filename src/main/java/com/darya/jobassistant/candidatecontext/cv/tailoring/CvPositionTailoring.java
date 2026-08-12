package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 2 correction: the tailoring decisions for one existing career position's own
 * (non-project) responsibilities/achievements, referenced by its stable source {@code
 * careerPositionId} - never a recreated company name, position title, employment dates, or company/
 * position ordering, all of which remain factual/application-owned. A {@link CvTailoringResult} may
 * carry zero or more of these, one per position it chooses to tailor.
 *
 * <p>{@code CvSourceSnapshot} carries career bullets at both the position level and the (nested)
 * project level; {@link CvProjectTailoring} already covered the latter, and this type closes the
 * gap for the former using the exact same reference types ({@link CvResponsibilityTailoring}/{@link
 * CvAchievementTailoring}) and the exact same intrinsic invariants - the AI-editable surface is now
 * symmetric with the factual data it draws from. Deliberately a separate record from {@link
 * CvProjectTailoring} rather than a shared generic "career node" type: keeping position and project
 * tailoring as distinct, explicitly-named contracts keeps each one easy to reason about and to
 * validate independently, and mirrors how the factual domain itself (e.g. {@code CareerPosition} vs
 * {@code CareerProject}) keeps them distinct.
 *
 * <p>{@link #responsibilities} and {@link #achievements} each express selection and presentation
 * order purely through list position - no id may appear twice within either list. The two lists are
 * independent id spaces and are intentionally never cross-checked against each other.
 *
 * <p>Whether {@link #careerPositionId} actually exists in a particular {@code CvSourceSnapshot},
 * and whether the referenced responsibility/achievement ids actually belong to that exact position
 * (as opposed to some other position, or a project), is intentionally NOT checked here - that
 * source-aware validation belongs to Sprint 11 Step 3's guardrails, not this structural contract.
 */
public record CvPositionTailoring(
        UUID careerPositionId,
        List<CvResponsibilityTailoring> responsibilities,
        List<CvAchievementTailoring> achievements
) {

    public CvPositionTailoring {
        if (careerPositionId == null) {
            throw new IllegalArgumentException("Position tailoring careerPositionId must not be null");
        }
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        CvTailoringValidation.requireNoDuplicateResponsibilityIds(responsibilities, "position tailoring");
        CvTailoringValidation.requireNoDuplicateAchievementIds(achievements, "position tailoring");
    }
}
