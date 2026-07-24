package com.darya.jobassistant.monitoring;

/**
 * Thrown when a monitoring run cannot proceed at all - e.g. the candidate profile could not be
 * loaded, so no vacancy in this run could possibly be analyzed. Per-vacancy failures (analysis,
 * reservation, notification) do not use this; they are counted in {@code failedCount} instead.
 */
public class JobMonitoringException extends RuntimeException {

    public JobMonitoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
