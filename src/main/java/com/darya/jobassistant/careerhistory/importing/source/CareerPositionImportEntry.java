package com.darya.jobassistant.careerhistory.importing.source;

import java.time.LocalDate;
import java.util.List;

/**
 * One position entry within a {@link CareerCompanyImportEntry} - source-shape counterpart of
 * {@code CareerPosition}.
 *
 * @param key stable import identity, required, pattern {@code [a-z0-9][a-z0-9._-]*}, max length
 *     100, unique among sibling positions within the same company
 * @param currentRole nullable at this layer (defaults to {@code false} during mapping); a {@code
 *     true} value combined with a non-null {@link #endDate} is a validation error
 */
public record CareerPositionImportEntry(
        String key,
        String title,
        String employmentType,
        String location,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        Boolean currentRole,
        String description,
        Integer displayOrder,
        List<CareerResponsibilityImportEntry> responsibilities,
        List<CareerAchievementImportEntry> achievements,
        List<CareerProjectImportEntry> projects
) {
}
