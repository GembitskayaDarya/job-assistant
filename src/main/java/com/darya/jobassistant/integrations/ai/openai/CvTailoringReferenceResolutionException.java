package com.darya.jobassistant.integrations.ai.openai;

/**
 * Thrown by {@link CvTailoringReferenceIndex} when the AI's CV tailoring response uses an unknown
 * skill reference token. Carries only the offending reference string and the namespace label that
 * was expected - never candidate or vacancy text, matching this codebase's diagnostic-safety
 * convention (see {@code CvTailoringViolation}'s identical ids-and-category-only design). {@code
 * ownerRef} is always {@code null} since Sprint 11 Final CV Policy simplified this index to a
 * single flat skill namespace with no parent/child ownership left to represent - kept as a field
 * only so this exception's shape stays uniform with the constructor signature the (now-simplified)
 * index still calls.
 *
 * <p>{@link SpringAiCvTailoringAdapter#tailorSkills} lets this propagate into its existing generic
 * malformed-response handling - the same classification path already used for a {@code
 * CvSkillTailoringResult} domain-invariant violation - so no new failure category is introduced at
 * the {@code CvTailoringAiPort} boundary. Never resolved by guessing, substituting by name, or
 * silently dropping the reference: resolution either succeeds with the exact real id, or fails
 * loudly here.
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
