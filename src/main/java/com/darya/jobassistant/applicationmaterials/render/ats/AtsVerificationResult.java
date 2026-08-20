package com.darya.jobassistant.applicationmaterials.render.ats;

import java.util.List;

/**
 * Sprint 11 Big Block 7: the immutable outcome of {@link AtsCvVerifier#verify} - structural/
 * mechanical PDF-readability verification only, never a predictive score for any particular ATS
 * vendor. {@link #status()} is always derived from {@link #violations} being empty - there is no
 * independently stored status that could ever disagree with the violation list, mirroring {@code
 * CvTailoringValidationResult#valid()}'s exact invariant.
 */
public record AtsVerificationResult(List<AtsVerificationViolation> violations) {

    public AtsVerificationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public AtsVerificationStatus status() {
        return violations.isEmpty() ? AtsVerificationStatus.ATS_READABLE : AtsVerificationStatus.NOT_ATS_READABLE;
    }

    public boolean readable() {
        return violations.isEmpty();
    }
}
