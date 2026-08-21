package com.darya.jobassistant.candidatecontext.cv.tailoring.ai;

/**
 * Sprint 11 Big Block 6: provider-neutral CV tailoring failure - covers both an AI provider/request
 * failure and a structurally malformed AI response. Mirrors {@code
 * applicationmaterials.generation.model.ApplicationMaterialsAiException}'s convention exactly: never
 * carries a Spring AI or OpenAI exception type, a raw response body, or the prompt - only a safe
 * message plus (where one exists) the original cause.
 *
 * <p>Sprint 11 production-hardening correction: {@link #reason} makes the provider-failure-vs-
 * malformed-response classification an explicit field rather than relying forever on {@code
 * getCause() != null} - callers (notably {@code CvTailoringUseCase}'s bounded tailoring retry, and
 * {@code GenerateApplicationMaterialsUseCase}'s failure-code mapping) need to branch on exactly this
 * distinction, and an explicit enum makes that contract impossible to get subtly wrong at a new call
 * site. Fully backward compatible: {@link #reason} is derived automatically from which constructor is
 * used - {@link #CvTailoringAiException(String, Throwable)} (a cause is present) always means {@link
 * Reason#PROVIDER_ERROR}, {@link #CvTailoringAiException(String)} (no cause) always means {@link
 * Reason#MALFORMED_RESPONSE} - so no existing call site anywhere in this codebase needed to change.
 */
public class CvTailoringAiException extends RuntimeException {

    /**
     * {@link #PROVIDER_ERROR} - a genuine provider/network/auth/rate-limit failure (network/auth/
     * rate-limit {@code RuntimeException} from the {@code ChatClient} call itself); never retried by
     * {@code CvTailoringUseCase}'s bounded tailoring retry, since a fresh call is not expected to
     * behave differently from the last one within the same request.
     *
     * <p>{@link #MALFORMED_RESPONSE} - the provider call itself succeeded, but its content was
     * structurally invalid (an unresolvable prompt-local reference, a domain-invariant violation, a
     * missing/empty result); stochastic by nature (a fresh LLM call can reasonably produce a valid
     * result), so this is the one and only reason {@code CvTailoringUseCase} retries.
     */
    public enum Reason {
        PROVIDER_ERROR,
        MALFORMED_RESPONSE
    }

    private final Reason reason;

    public CvTailoringAiException(String message) {
        super(message);
        this.reason = Reason.MALFORMED_RESPONSE;
    }

    public CvTailoringAiException(String message, Throwable cause) {
        super(message, cause);
        this.reason = Reason.PROVIDER_ERROR;
    }

    public Reason reason() {
        return reason;
    }
}
