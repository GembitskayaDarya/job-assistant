package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.UUID;

/**
 * Sprint 11 Step 2: one project-level responsibility tailoring decision - references an existing
 * source {@code careerResponsibilityId} rather than recreating any text. Selection and presentation
 * order are both expressed by this record's position within {@link
 * CvProjectTailoring#responsibilities()} alone - no separate ordering field, to avoid redundantly
 * representing the same information twice (mirrors {@link CvTailoringResult#orderedSkillIds()}).
 *
 * <p>{@link #rewrittenText}, when present, must not be blank - {@code null} means "show the
 * original {@code CvSourceResponsibility} text unchanged," never an empty override. The rewrite
 * always stays bound to {@link #careerResponsibilityId}, the exact source bullet it replaces - the
 * AI is never allowed to submit a rewrite unassociated with a real responsibility reference.
 *
 * <p>Whether {@link #careerResponsibilityId} actually exists in a particular {@code
 * CvSourceSnapshot} - or belongs to this decision's owning project at all - is intentionally NOT
 * checked here; that source-aware validation belongs to Sprint 11 Step 3's guardrails, not this
 * structural contract.
 */
public record CvResponsibilityTailoring(UUID careerResponsibilityId, String rewrittenText) {

    public CvResponsibilityTailoring {
        if (careerResponsibilityId == null) {
            throw new IllegalArgumentException("Responsibility tailoring careerResponsibilityId must not be null");
        }
        if (rewrittenText != null && rewrittenText.isBlank()) {
            throw new IllegalArgumentException("Responsibility tailoring rewrittenText must not be blank when present");
        }
    }
}
