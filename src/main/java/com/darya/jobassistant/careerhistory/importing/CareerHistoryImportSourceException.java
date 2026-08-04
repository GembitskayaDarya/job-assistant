package com.darya.jobassistant.careerhistory.importing;

/**
 * Thrown by {@link CareerHistoryImportSource#load()} when the configured source is missing,
 * unreadable, exceeds the configured maximum size, or is not valid, fully-recognized YAML.
 * Framework-free, matching {@code CandidateProfileMigrationSourceException}'s convention at this
 * boundary. Carries only a safe, generic message and resource location metadata - never file
 * contents.
 */
public class CareerHistoryImportSourceException extends RuntimeException {

    public CareerHistoryImportSourceException(String message) {
        super(message);
    }

    public CareerHistoryImportSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
