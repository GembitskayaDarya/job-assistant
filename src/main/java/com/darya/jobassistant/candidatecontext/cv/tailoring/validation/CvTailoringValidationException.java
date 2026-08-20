package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

import java.util.List;

/**
 * Sprint 11 Step 7: thrown by a caller (never by {@link CvTailoringValidator} itself, which only
 * ever returns a {@link CvTailoringValidationResult}) when a {@code CvTailoringResult} fails
 * source-aware validation and must not proceed to assembly. Carries {@link #violations} - ids plus
 * typed categories only, the same privacy-by-design shape {@link CvTailoringViolation} already
 * guarantees - never raw candidate content, so this exception is safe to log without redaction.
 *
 * <p>Deliberately a distinct type from {@code applicationmaterials.generation.model.ApplicationMaterialsAiException}:
 * the two live in different, independently-boundary-tested packages (this one must stay reachable
 * from {@code candidatecontext.cv.tailoring.validation} without ever pulling in that unrelated
 * feature's exception hierarchy), and a source-validation failure is a materially different problem
 * from an AI provider/malformed-response failure - see {@code
 * candidatecontext.cv.tailoring.ai.CvTailoringAiException} for that one.
 */
public class CvTailoringValidationException extends RuntimeException {

    private final transient List<CvTailoringViolation> violations;

    public CvTailoringValidationException(List<CvTailoringViolation> violations) {
        super("CV tailoring result failed source-aware validation with " + safeSize(violations) + " violation(s)");
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<CvTailoringViolation> violations() {
        return violations;
    }

    private static int safeSize(List<CvTailoringViolation> violations) {
        return violations == null ? 0 : violations.size();
    }
}
