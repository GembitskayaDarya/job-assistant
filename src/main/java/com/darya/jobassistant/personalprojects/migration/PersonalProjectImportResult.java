package com.darya.jobassistant.personalprojects.migration;

/**
 * Sprint 11 Step 5 acceptance correction: a safe-to-log summary of one {@link
 * PersonalProjectImportUseCase#apply} call - counts only, never project name/description/
 * highlight/technology content, matching this codebase's logging-safety convention (see {@code
 * CandidateProfileMigrationResult}).
 */
public record PersonalProjectImportResult(int sourceCount, int created, int updated, int unchanged) {
}
