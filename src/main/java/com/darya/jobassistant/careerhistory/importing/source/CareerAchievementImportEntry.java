package com.darya.jobassistant.careerhistory.importing.source;

/**
 * One achievement bullet, shared shape for both position-level and project-level achievements -
 * see {@link CareerResponsibilityImportEntry}'s javadoc for the identity-derivation rationale,
 * which applies identically here.
 */
public record CareerAchievementImportEntry(
        String text,
        Integer displayOrder
) {
}
