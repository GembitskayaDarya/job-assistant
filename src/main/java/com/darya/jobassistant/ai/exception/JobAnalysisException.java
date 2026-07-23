package com.darya.jobassistant.ai.exception;

public class JobAnalysisException extends RuntimeException {

    public JobAnalysisException(String message) {
        super(message);
    }

    public JobAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
