package com.darya.jobassistant.vacancyimport.dto;

/**
 * Provider-independent summary of one {@link com.darya.jobassistant.vacancyimport.ExpireVacancyImportSessionsUseCase}
 * batch. {@code skippedCount} is not a failure: it counts candidates whose conditional expiration
 * update applied to zero rows because the user (or another operation) resolved the session between
 * selection and the update - normal, expected concurrency, not an error condition.
 */
public record ExpireVacancyImportSessionsResult(int candidateCount, int expiredCount, int skippedCount, int failedCount) {

    private static final ExpireVacancyImportSessionsResult EMPTY = new ExpireVacancyImportSessionsResult(0, 0, 0, 0);

    public ExpireVacancyImportSessionsResult {
        requireNonNegative("candidateCount", candidateCount);
        requireNonNegative("expiredCount", expiredCount);
        requireNonNegative("skippedCount", skippedCount);
        requireNonNegative("failedCount", failedCount);
    }

    public static ExpireVacancyImportSessionsResult empty() {
        return EMPTY;
    }

    private static void requireNonNegative(String fieldName, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative, but was " + value);
        }
    }
}
