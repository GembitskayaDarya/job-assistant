package com.darya.jobassistant.careerhistory.importing.source;

/**
 * One responsibility bullet, shared shape for both position-level and project-level
 * responsibilities. Has no {@code key} - unlike company/position/project, its identity is derived
 * from its parent's import path plus {@link #displayOrder} alone (see {@code
 * CareerHistoryImportIdGenerator}), since a responsibility bullet has no natural stable business
 * key of its own.
 */
public record CareerResponsibilityImportEntry(
        String text,
        Integer displayOrder
) {
}
