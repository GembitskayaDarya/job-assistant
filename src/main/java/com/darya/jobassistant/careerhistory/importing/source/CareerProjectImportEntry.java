package com.darya.jobassistant.careerhistory.importing.source;

import java.time.LocalDate;
import java.util.List;

/**
 * One project entry within a {@link CareerPositionImportEntry} - source-shape counterpart of
 * {@code CareerProject}.
 *
 * @param key stable import identity, required, pattern {@code [a-z0-9][a-z0-9._-]*}, max length
 *     100, unique among sibling projects within the same position
 */
public record CareerProjectImportEntry(
        String key,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Integer displayOrder,
        List<CareerResponsibilityImportEntry> responsibilities,
        List<CareerAchievementImportEntry> achievements,
        List<CareerTechnologyImportEntry> technologies
) {
}
