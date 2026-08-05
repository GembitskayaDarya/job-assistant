package com.darya.jobassistant.candidatecontext;

/**
 * Sprint 9 Step 8: the one application/domain-facing port for loading the reusable {@link
 * CandidateContextSnapshot} - Candidate Profile plus optional Career History, read together. A
 * different use case from {@code com.darya.jobassistant.candidates.CandidateProfileProvider},
 * which remains the startup-validation path; this interface deliberately does not extend it.
 *
 * <p>Hides the configured runtime profile key and every persistence detail behind one method -
 * implementations must return framework-free models only, never a JPA entity or {@code
 * @ConfigurationProperties} object, and must never create Career History as a side effect of a
 * read.
 */
public interface CandidateContextProvider {

    /**
     * @throws com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException if
     *     no persistent Candidate Profile exists for the configured runtime profile key
     */
    CandidateContextSnapshot loadCurrentContext();
}
