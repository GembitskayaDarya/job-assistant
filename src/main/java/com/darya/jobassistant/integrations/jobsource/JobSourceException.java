package com.darya.jobassistant.integrations.jobsource;

public class JobSourceException extends RuntimeException {

    public JobSourceException(String message) {
        super(message);
    }

    public JobSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
