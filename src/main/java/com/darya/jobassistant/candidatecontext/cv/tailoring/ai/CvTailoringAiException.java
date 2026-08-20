package com.darya.jobassistant.candidatecontext.cv.tailoring.ai;

/**
 * Sprint 11 Big Block 6: provider-neutral CV tailoring failure - covers both an AI provider/request
 * failure and a structurally malformed AI response. Mirrors {@code
 * applicationmaterials.generation.model.ApplicationMaterialsAiException}'s convention exactly: never
 * carries a Spring AI or OpenAI exception type, a raw response body, or the prompt - only a safe
 * message plus (where one exists) the original cause. A cause present means a genuine provider-level
 * {@code RuntimeException} (network/auth/rate-limit) was wrapped; no cause means the implementation
 * itself detected a structurally invalid response (e.g. an unparseable id) - callers distinguish the
 * two the same way {@code GenerateApplicationMaterialsUseCase} already does for its own AI port.
 */
public class CvTailoringAiException extends RuntimeException {

    public CvTailoringAiException(String message) {
        super(message);
    }

    public CvTailoringAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
