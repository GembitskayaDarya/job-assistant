package com.darya.jobassistant.careerhistory.aggregate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 9 Step 6: one project within a {@link CareerPosition} (V19's {@code career_project} plus
 * its responsibility/achievement/technology children) - framework-free, immutable.
 *
 * <p>{@link #startDate}/{@link #endDate} are both optional, unlike the owning position's start
 * date - a project's timeline is frequently vaguer than the position that contains it. This
 * record only validates the two dates against each other when both are present; validating them
 * against the owning position's dates is {@link CareerPosition}'s job (it is the one that knows
 * both), not this one's.
 */
public record CareerProject(
        UUID id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int displayOrder,
        List<CareerResponsibility> responsibilities,
        List<CareerAchievement> achievements,
        List<CareerTechnology> technologies
) {

    public CareerProject {
        name = CareerHistoryValidation.requireNonBlank(name, "Project name must not be blank");
        description = CareerHistoryValidation.blankToNull(description);
        CareerHistoryValidation.requireNonNegative(displayOrder, "Project display order must not be negative");
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Project end date must not precede start date");
        }

        responsibilities = CareerHistoryValidation.copySortedByDisplayOrder(responsibilities, CareerResponsibility::displayOrder);
        achievements = CareerHistoryValidation.copySortedByDisplayOrder(achievements, CareerAchievement::displayOrder);
        technologies = CareerHistoryValidation.copySortedByDisplayOrder(technologies, CareerTechnology::displayOrder);

        CareerHistoryValidation.requireUniqueDisplayOrders(
                responsibilities, CareerResponsibility::displayOrder, "Duplicate project responsibility display order");
        CareerHistoryValidation.requireUniqueDisplayOrders(
                achievements, CareerAchievement::displayOrder, "Duplicate project achievement display order");
        CareerHistoryValidation.requireUniqueDisplayOrders(
                technologies, CareerTechnology::displayOrder, "Duplicate project technology display order");
        CareerHistoryValidation.requireUniqueBy(technologies, CareerTechnology::name, "Duplicate technology name in one project");
    }
}
