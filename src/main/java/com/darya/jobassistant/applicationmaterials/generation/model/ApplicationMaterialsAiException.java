package com.darya.jobassistant.applicationmaterials.generation.model;

/**
 * Provider-neutral application-materials generation failure - covers both an AI provider/request
 * failure and a structurally malformed AI response. Mirrors {@code JobAnalysisException}/{@code
 * VacancyExtractionException}'s convention exactly: never carries a Spring AI or OpenAI exception
 * type, a raw response body, or the prompt - only a safe message plus (where one exists) the
 * original cause, which callers must never persist verbatim (see {@code
 * GenerateApplicationMaterialsUseCase}'s failure-message handling).
 */
public class ApplicationMaterialsAiException extends RuntimeException {

    public ApplicationMaterialsAiException(String message) {
        super(message);
    }

    public ApplicationMaterialsAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
