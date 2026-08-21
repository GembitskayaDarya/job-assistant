package com.darya.jobassistant.integrations.ai.openai;

/**
 * Thrown by {@link CvTailoringReferenceIndex} when the AI's CV tailoring response uses a
 * prompt-local reference that does not exist, belongs to a different namespace than the field
 * expected, or belongs to the right namespace but the wrong owner (e.g. a technology reference from
 * a sibling project). Carries only the offending reference string, the namespace label that was
 * expected, and (when relevant) the owner reference it was claimed under - never candidate or
 * vacancy text, matching this codebase's diagnostic-safety convention (see {@code
 * CvTailoringViolation}'s identical ids-and-category-only design).
 *
 * <p>{@link SpringAiCvTailoringAdapter#tailor} lets this propagate into its existing generic
 * malformed-response handling - the same classification path already used for a {@code
 * CvTailoringResult} domain-invariant violation - so no new failure category is introduced at the
 * {@code CvTailoringAiPort} boundary. Never resolved by guessing, substituting by name, or silently
 * dropping the reference: resolution either succeeds with the exact real id, or fails loudly here.
 */
final class CvTailoringReferenceResolutionException extends RuntimeException {

    private final String ref;
    private final String expectedNamespace;
    private final String ownerRef;

    CvTailoringReferenceResolutionException(String message, String ref, String expectedNamespace, String ownerRef) {
        super(message);
        this.ref = ref;
        this.expectedNamespace = expectedNamespace;
        this.ownerRef = ownerRef;
    }

    String ref() {
        return ref;
    }

    String expectedNamespace() {
        return expectedNamespace;
    }

    String ownerRef() {
        return ownerRef;
    }
}
