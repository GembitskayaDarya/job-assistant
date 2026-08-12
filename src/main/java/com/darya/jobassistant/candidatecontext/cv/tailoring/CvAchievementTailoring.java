package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.UUID;

/**
 * Sprint 11 Step 2: one project-level achievement tailoring decision - the achievement counterpart
 * of {@link CvResponsibilityTailoring}, kept as a separate type rather than a generic "bullet"
 * because the factual Career History itself distinguishes responsibilities from achievements (see
 * {@code CareerResponsibility}/{@code CareerAchievement} and their {@code CvSource*} counterparts) -
 * collapsing the two here would lose a distinction the rest of the domain preserves everywhere else.
 *
 * <p>References an existing source {@code careerAchievementId} rather than recreating any text.
 * Selection and presentation order are both expressed by this record's position within {@link
 * CvProjectTailoring#achievements()} alone - no separate ordering field.
 *
 * <p>{@link #rewrittenText}, when present, must not be blank - {@code null} means "show the
 * original {@code CvSourceAchievement} text unchanged," never an empty override.
 *
 * <p>Whether {@link #careerAchievementId} actually exists in a particular {@code CvSourceSnapshot}
 * - or belongs to this decision's owning project at all - is intentionally NOT checked here; that
 * source-aware validation belongs to Sprint 11 Step 3's guardrails, not this structural contract.
 */
public record CvAchievementTailoring(UUID careerAchievementId, String rewrittenText) {

    public CvAchievementTailoring {
        if (careerAchievementId == null) {
            throw new IllegalArgumentException("Achievement tailoring careerAchievementId must not be null");
        }
        if (rewrittenText != null && rewrittenText.isBlank()) {
            throw new IllegalArgumentException("Achievement tailoring rewrittenText must not be blank when present");
        }
    }
}
