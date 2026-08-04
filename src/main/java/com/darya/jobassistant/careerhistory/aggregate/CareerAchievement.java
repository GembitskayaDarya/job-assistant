package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Sprint 9 Step 6: one ordered achievement bullet, shared shape for both position-level and
 * project-level achievements (V19's {@code career_position_achievement}/{@code
 * career_project_achievement}) - framework-free, immutable. Qualitative achievements (no numeric
 * metric) are valid; nothing here requires one.
 *
 * <p>{@link #id} is {@code null} for a not-yet-persisted bullet; a non-null id is preserved across
 * an update (see {@code CareerHistoryRepositoryAdapter}) and is never itself part of any
 * duplicate/uniqueness check in this package.
 */
public record CareerAchievement(UUID id, String text, int displayOrder) {

    public CareerAchievement {
        text = CareerHistoryValidation.requireNonBlank(text, "Achievement text must not be blank");
        CareerHistoryValidation.requireNonNegative(displayOrder, "Achievement display order must not be negative");
    }
}
