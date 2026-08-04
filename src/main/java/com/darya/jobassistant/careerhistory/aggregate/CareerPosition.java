package com.darya.jobassistant.careerhistory.aggregate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 9 Step 6: one position within a {@link CareerCompany} (V19's {@code career_position}
 * plus its responsibility/achievement/project children) - framework-free, immutable.
 *
 * <p>Overlapping employment periods and more than one {@link #currentRole} across the aggregate
 * are both deliberately allowed - concurrent contracts are a real, valid scenario, and nothing at
 * this level (or {@link CareerCompany}/{@link CareerHistoryAggregate}) checks for overlap.
 *
 * <p>This is also the one place that validates each {@link CareerProject}'s dates against this
 * position's own dates (the project itself only knows its own two dates, not its parent's) - a
 * project's start must not precede this position's start, and, when this position has an end
 * date, a project's end must not exceed it. A project missing one or both dates is never rejected
 * for that alone.
 */
public record CareerPosition(
        UUID id,
        String title,
        String employmentType,
        String location,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        String description,
        int displayOrder,
        List<CareerResponsibility> responsibilities,
        List<CareerAchievement> achievements,
        List<CareerProject> projects
) {

    public CareerPosition {
        title = CareerHistoryValidation.requireNonBlank(title, "Position title must not be blank");
        if (startDate == null) {
            throw new IllegalArgumentException("Position start date must not be null");
        }
        employmentType = CareerHistoryValidation.blankToNull(employmentType);
        location = CareerHistoryValidation.blankToNull(location);
        workArrangement = CareerHistoryValidation.blankToNull(workArrangement);
        description = CareerHistoryValidation.blankToNull(description);
        CareerHistoryValidation.requireNonNegative(displayOrder, "Position display order must not be negative");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Position end date must not precede start date");
        }
        if (currentRole && endDate != null) {
            throw new IllegalArgumentException("A current role must not have an end date");
        }

        responsibilities = CareerHistoryValidation.copySortedByDisplayOrder(responsibilities, CareerResponsibility::displayOrder);
        achievements = CareerHistoryValidation.copySortedByDisplayOrder(achievements, CareerAchievement::displayOrder);
        projects = CareerHistoryValidation.copySortedByDisplayOrder(projects, CareerProject::displayOrder);

        CareerHistoryValidation.requireUniqueDisplayOrders(
                responsibilities, CareerResponsibility::displayOrder, "Duplicate position responsibility display order");
        CareerHistoryValidation.requireUniqueDisplayOrders(
                achievements, CareerAchievement::displayOrder, "Duplicate position achievement display order");
        CareerHistoryValidation.requireUniqueDisplayOrders(projects, CareerProject::displayOrder, "Duplicate project display order");
        CareerHistoryValidation.requireUniqueBy(projects, CareerProject::name, "Duplicate project name in one position");

        for (CareerProject project : projects) {
            requireProjectDatesWithinPosition(project, startDate, endDate);
        }
    }

    private static void requireProjectDatesWithinPosition(CareerProject project, LocalDate positionStart, LocalDate positionEnd) {
        if (project.startDate() != null && project.startDate().isBefore(positionStart)) {
            throw new IllegalArgumentException(
                    "Project '" + project.name() + "' must not start before its position's start date");
        }
        if (positionEnd != null && project.endDate() != null && project.endDate().isAfter(positionEnd)) {
            throw new IllegalArgumentException(
                    "Project '" + project.name() + "' must not end after its position's end date");
        }
    }
}
