package com.darya.jobassistant.careerhistory.importing.source;

import java.util.List;

/**
 * Sprint 9 Step 7: the root of one external Career History import YAML document - framework-free
 * (no Spring/JPA annotations; Jackson deserializes Java records natively as of the version this
 * project's Spring Boot BOM manages, so no extra annotations are needed here either) and separate
 * from {@code CareerHistoryAggregate}, which this document is only ever mapped <em>into</em> by
 * {@code CareerHistoryImportMapper} - never the reverse.
 *
 * <p>Every field is deliberately nullable at this parsing layer: a missing/blank required field is
 * a validation error with a safe, path-based message ({@code
 * com.darya.jobassistant.careerhistory.importing.CareerHistoryImportValidator}), not a parse
 * failure with a raw Jackson stack trace. Unknown YAML properties still fail parsing outright
 * (Jackson's default {@code FAIL_ON_UNKNOWN_PROPERTIES=true}, never relaxed for this type) -
 * intentionally stricter than "extra fields are ignored", so a typo'd or unsupported field is
 * never silently dropped.
 *
 * @param schemaVersion required; must currently equal {@code 1}
 * @param candidateProfileKey required, non-blank - resolved through {@code
 *     CandidateProfileRepositoryPort}, never a hardcoded id
 * @param expectedVersion nullable - required only when the destination Career History already
 *     exists and differs from the source (see the decision matrix in {@code
 *     CareerHistoryImportUseCase}); must be non-negative when present
 * @param companies must not be null; may be empty (the domain aggregate itself allows an empty
 *     company list)
 */
public record CareerHistoryImportDocument(
        Integer schemaVersion,
        String candidateProfileKey,
        Long expectedVersion,
        List<CareerCompanyImportEntry> companies
) {
}
