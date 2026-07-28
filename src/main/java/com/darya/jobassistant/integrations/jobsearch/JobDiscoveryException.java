package com.darya.jobassistant.integrations.jobsearch;

/**
 * Thrown by a {@link JobSearchPort} or {@link JobPageFetchPort} implementation when a search or
 * page fetch fails. The message must be a safe, application-level description - never a raw
 * provider response body, API key, or SDK-specific exception detail.
 */
public class JobDiscoveryException extends RuntimeException {

    public JobDiscoveryException(String message) {
        super(message);
    }

    public JobDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
