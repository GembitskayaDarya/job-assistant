package com.darya.jobassistant.vacancyextraction.exception;

/**
 * Provider-neutral extraction failure. Never carries a Spring AI or OpenAI exception type, a raw
 * response body, or the prompt - only a safe message plus (where one exists) the original cause,
 * mirroring {@code JobAnalysisException}.
 */
public class VacancyExtractionException extends RuntimeException {

    public VacancyExtractionException(String message) {
        super(message);
    }

    public VacancyExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
