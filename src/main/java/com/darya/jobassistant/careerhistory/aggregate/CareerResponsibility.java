package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Sprint 9 Step 6: one ordered responsibility bullet, shared shape for both position-level and
 * project-level responsibilities (V19's {@code career_position_responsibility}/{@code
 * career_project_responsibility}) - framework-free, immutable.
 *
 * <p>{@link #id} is {@code null} for a not-yet-persisted bullet; a non-null id is preserved across
 * an update (see {@code CareerHistoryRepositoryAdapter}) and is never itself part of any
 * duplicate/uniqueness check in this package.
 */
public record CareerResponsibility(UUID id, String text, int displayOrder) {

    public CareerResponsibility {
        text = CareerHistoryValidation.requireNonBlank(text, "Responsibility text must not be blank");
        CareerHistoryValidation.requireNonNegative(displayOrder, "Responsibility display order must not be negative");
    }
}
