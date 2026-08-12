package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

import java.util.UUID;

/**
 * Sprint 11 Step 3: one structured reference-integrity/ownership problem found by {@link
 * CvTailoringValidator}. Deliberately carries only ids and a typed {@link #category} - no free-form
 * message and no raw CV content (skill name, bullet text, ...) - IDs plus category are sufficient
 * for logging/metrics/debugging, and avoid leaking candidate content into structured diagnostics
 * unnecessarily.
 *
 * <p>{@link #referencedId} is the id the tailoring result actually referenced (the skill,
 * responsibility, achievement, position, or project id that failed validation). {@link #parentId}
 * is the owning position/project id the reference was claimed under - {@code null} for the three
 * {@code UNKNOWN_*_REFERENCE} categories, which have no parent to report (the reference itself is
 * the thing that could not be found).
 */
public record CvTailoringViolation(CvTailoringViolationCategory category, UUID referencedId, UUID parentId) {

    public CvTailoringViolation {
        if (category == null) {
            throw new IllegalArgumentException("Tailoring violation category must not be null");
        }
        if (referencedId == null) {
            throw new IllegalArgumentException("Tailoring violation referencedId must not be null");
        }
    }
}
