package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

import java.util.List;

/**
 * Sprint 11 Step 3: the immutable outcome of validating one {@code CvTailoringResult} against the
 * {@code CvSourceSnapshot} it was produced from. {@link #valid()} is always derived from {@link
 * #violations} being empty - there is no independently stored boolean that could ever disagree with
 * the violation list.
 *
 * <p>{@link #violations} preserves the order {@link CvTailoringValidator} discovered them in -
 * deterministic for a given input, never re-sorted or grouped - so repeated validation of
 * equivalent input yields an equivalent, equal result.
 */
public record CvTailoringValidationResult(List<CvTailoringViolation> violations) {

    public CvTailoringValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
