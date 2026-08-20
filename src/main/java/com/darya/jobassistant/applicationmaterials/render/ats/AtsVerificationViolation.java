package com.darya.jobassistant.applicationmaterials.render.ats;

/**
 * Sprint 11 Big Block 7: one structural ATS-readability problem found by {@link AtsCvVerifier}.
 * {@link #detail} is a short, generic, non-sensitive description (a section/field name, or the
 * factual value that failed to extract, e.g. a company/position name already present on the CV
 * itself) - never free-form bullet/summary prose, mirroring {@code CvTailoringViolation}'s
 * privacy-by-design shape.
 */
public record AtsVerificationViolation(AtsViolationCategory category, String detail) {

    public AtsVerificationViolation {
        if (category == null) {
            throw new IllegalArgumentException("ATS verification violation category must not be null");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("ATS verification violation detail must not be blank");
        }
    }
}
