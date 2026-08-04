package com.darya.jobassistant.careerhistory.config;

/**
 * Thrown by {@link CareerHistoryStartupExclusivityValidator} when {@code
 * candidate-profile.migration.mode} and {@code career-history.import.mode} are both an active
 * mode ({@code DRY_RUN}/{@code APPLY}) in the same application startup. Framework-free at this
 * boundary (a plain {@link RuntimeException}), matching every other focused exception in this
 * workflow - carries only a safe, generic, actionable message, never any configuration value or
 * candidate data.
 */
public class CareerHistoryImportStartupConflictException extends RuntimeException {

    public CareerHistoryImportStartupConflictException() {
        super("Candidate Profile migration and Career History import cannot run in the same application "
                + "startup. Run the operations sequentially.");
    }
}
