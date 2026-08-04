package com.darya.jobassistant.careerhistory.importing.source;

import java.util.List;

/**
 * One company entry in a {@link CareerHistoryImportDocument} - source-shape counterpart of {@code
 * CareerCompany}, plus the {@link #key} stable import identity that the domain aggregate itself
 * does not carry (see {@code CareerHistoryImportIdGenerator}).
 *
 * @param key stable import identity, required, pattern {@code [a-z0-9][a-z0-9._-]*}, max length
 *     100, unique among sibling companies in the document - identity, not a display value; never
 *     copied into {@link #name} or any other domain-facing field
 * @param displayOrder explicit, required business ordering - never derived from dates
 */
public record CareerCompanyImportEntry(
        String key,
        String name,
        String website,
        String industry,
        String location,
        String description,
        Integer displayOrder,
        List<CareerPositionImportEntry> positions
) {
}
