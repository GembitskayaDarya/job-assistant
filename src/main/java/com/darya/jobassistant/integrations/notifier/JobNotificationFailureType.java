package com.darya.jobassistant.integrations.notifier;

/**
 * Transport-independent classification of why a {@link JobNotificationPort} send failed.
 */
public enum JobNotificationFailureType {

    /** Retrying the same notification later may succeed, e.g. a transient network or provider outage. */
    TEMPORARY_FAILURE,

    /** Retrying the same notification without changing input or configuration is unlikely to succeed. */
    PERMANENT_FAILURE,

    /** The failure could not be safely classified as temporary or permanent. */
    UNEXPECTED_FAILURE,

    /**
     * The rendered message could not fit within the provider's single-message limit even after
     * deterministic truncation. Raised before any provider HTTP call is made - never a genuine
     * send failure. Retrying without changing the content is never expected to help.
     */
    PAYLOAD_TOO_LARGE
}
