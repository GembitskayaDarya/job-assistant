package com.darya.jobassistant.candidates.migration;

import java.util.List;

/**
 * The complete, structured outcome of one {@link CandidateProfileMigrationUseCase#dryRun}/{@link
 * CandidateProfileMigrationUseCase#apply} call. Deliberately carries no full profile content -
 * safe to log in full (see {@code CandidateProfileMigrationRunner}).
 *
 * @param semanticallyEqual {@code true} only for {@code WOULD_NO_OP}/{@code NO_OP} - for {@code
 *     WOULD_CREATE}/{@code CREATED} there is no destination to be equal to, so this is {@code
 *     false} even though nothing actually conflicted
 * @param changedFields safe field/category names that differ between source and destination (see
 *     {@link CandidateProfileDiff}) - empty for {@code WOULD_CREATE}/{@code CREATED}/{@code
 *     WOULD_NO_OP}/{@code NO_OP}
 * @param warnings safe, human-readable messages - e.g. the reason for {@code VALIDATION_FAILED}
 * @param resultingVersion the destination's version after this call - unchanged for {@code
 *     NO_OP}/{@code CONFLICT}/{@code DRY_RUN} (when a destination exists), the freshly created
 *     version for {@code CREATED}, {@code null} when no destination exists or could be determined
 */
public record CandidateProfileMigrationResult(
        CandidateProfileMigrationMode mode,
        String profileKey,
        CandidateProfileMigrationStatus status,
        String sourceFingerprint,
        boolean destinationExists,
        boolean semanticallyEqual,
        List<String> changedFields,
        List<String> warnings,
        int skillCount,
        int languageCount,
        int preferenceCount,
        Long resultingVersion
) {
    public CandidateProfileMigrationResult {
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
