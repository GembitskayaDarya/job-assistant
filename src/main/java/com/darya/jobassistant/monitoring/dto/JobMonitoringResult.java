package com.darya.jobassistant.monitoring.dto;

public record JobMonitoringResult(
        int fetchedCount,
        int persistedCount,
        int analyzedCount,
        int matchedCount,
        int notifiedCount,
        int failedCount
) {
    private static final JobMonitoringResult EMPTY = new JobMonitoringResult(0, 0, 0, 0, 0, 0);

    public JobMonitoringResult {
        requireNonNegative("fetchedCount", fetchedCount);
        requireNonNegative("persistedCount", persistedCount);
        requireNonNegative("analyzedCount", analyzedCount);
        requireNonNegative("matchedCount", matchedCount);
        requireNonNegative("notifiedCount", notifiedCount);
        requireNonNegative("failedCount", failedCount);
    }

    public static JobMonitoringResult empty() {
        return EMPTY;
    }

    private static void requireNonNegative(String fieldName, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative, but was " + value);
        }
    }
}
