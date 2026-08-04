package com.darya.jobassistant.careerhistory.importing.source;

/**
 * One technology tag under a {@link CareerProjectImportEntry}. Has no {@code key}; its identity
 * is derived from its parent's import path, {@link #displayOrder}, and its own normalized {@link
 * #name} (see {@code CareerHistoryImportIdGenerator}) - unlike a plain responsibility/achievement
 * bullet, a technology's identity is meaningfully tied to which technology it names, not only to
 * its position in the list, while still remaining insensitive to capitalization.
 */
public record CareerTechnologyImportEntry(
        String name,
        String category,
        Integer displayOrder
) {
}
