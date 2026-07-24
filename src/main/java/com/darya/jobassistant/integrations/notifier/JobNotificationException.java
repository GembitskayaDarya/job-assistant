package com.darya.jobassistant.integrations.notifier;

/**
 * Thrown by a {@link JobNotificationPort} implementation when a notification could not be sent.
 * The message must be a safe, application-level description - never a raw provider response
 * body, bot token, or SDK-specific exception detail.
 */
public class JobNotificationException extends RuntimeException {

    private final JobNotificationFailureType failureType;

    public JobNotificationException(JobNotificationFailureType failureType, String message) {
        this(failureType, message, null);
    }

    public JobNotificationException(JobNotificationFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        if (failureType == null) {
            throw new IllegalArgumentException("failureType must not be null");
        }
        this.failureType = failureType;
    }

    public JobNotificationFailureType failureType() {
        return failureType;
    }
}
