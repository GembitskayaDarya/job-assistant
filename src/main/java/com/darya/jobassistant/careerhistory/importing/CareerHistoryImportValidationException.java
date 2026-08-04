package com.darya.jobassistant.careerhistory.importing;

import java.util.List;

/**
 * Thrown by {@link CareerHistoryImportValidator#validate} when the source document fails one or
 * more validation rules. Carries every independent {@link CareerHistoryImportViolation} found
 * (not just the first) - see the validator's javadoc for why fail-fast is avoided where
 * practical. The exception message itself is a bounded summary (count plus the first few
 * violations' paths/types) - the complete list is always available via {@link #violations()} for
 * a caller that needs it, never implicitly dumped into a log line in full.
 */
public class CareerHistoryImportValidationException extends RuntimeException {

    private static final int MAX_VIOLATIONS_IN_MESSAGE = 5;

    private final transient List<CareerHistoryImportViolation> violations;

    public CareerHistoryImportValidationException(List<CareerHistoryImportViolation> violations) {
        super(buildMessage(violations));
        this.violations = List.copyOf(violations);
    }

    public List<CareerHistoryImportViolation> violations() {
        return violations;
    }

    private static String buildMessage(List<CareerHistoryImportViolation> violations) {
        StringBuilder message = new StringBuilder("Career history import source failed validation with ")
                .append(violations.size()).append(" violation(s): ");
        for (int i = 0; i < Math.min(violations.size(), MAX_VIOLATIONS_IN_MESSAGE); i++) {
            if (i > 0) {
                message.append("; ");
            }
            message.append(violations.get(i));
        }
        if (violations.size() > MAX_VIOLATIONS_IN_MESSAGE) {
            message.append("; ... and ").append(violations.size() - MAX_VIOLATIONS_IN_MESSAGE).append(" more");
        }
        return message.toString();
    }
}
