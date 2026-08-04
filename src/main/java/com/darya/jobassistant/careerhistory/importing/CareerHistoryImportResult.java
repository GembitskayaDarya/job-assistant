package com.darya.jobassistant.careerhistory.importing;

/**
 * The complete, structured outcome of one {@link CareerHistoryImportUseCase#dryRun}/{@link
 * CareerHistoryImportUseCase#apply} call. Deliberately carries no full Career History content -
 * safe to log in full, matching {@code CandidateProfileMigrationResult}'s convention: {@link
 * #diff} is a {@link CareerHistoryDiff}, whose own javadoc documents exactly what it excludes.
 *
 * @param destinationFingerprint {@code null} when no destination existed
 * @param previousVersion the destination's version before this call, {@code null} when no
 *     destination existed
 * @param resultingVersion the destination's version after this call - the freshly created/updated
 *     version for {@code CREATED}/{@code UPDATED}, unchanged for {@code NO_OP}/{@code CONFLICT},
 *     {@code null} for every {@code DRY_RUN} status (a dry run never changes anything to report a
 *     "resulting" version for) and when no destination could be determined
 */
public record CareerHistoryImportResult(
        CareerHistoryImportMode mode,
        CareerHistoryImportStatus status,
        String candidateProfileKey,
        String sourceFingerprint,
        String destinationFingerprint,
        Long previousVersion,
        Long resultingVersion,
        CareerHistoryDiff diff
) {
}
