package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateProfile;

/**
 * Sprint 9 Step 4: the migration-only counterpart to {@code CandidateProfileProvider} - the
 * runtime abstraction {@code JobAnalysisService} actually reads from. This is deliberately a
 * separate interface, not a second implementation of {@code CandidateProfileProvider}: {@link
 * CandidateProfileMigrationRunner} depends on this abstraction alone, so normal runtime code can
 * never accidentally resolve a YAML-backed source, and this source is never used as a fallback if
 * the PostgreSQL-backed runtime provider fails.
 *
 * <p>The one production implementation, {@code YamlCandidateProfileMigrationSource}, performs no
 * PostgreSQL access and exists as a Spring bean only while {@code candidate-profile.migration.mode}
 * is configured at all.
 */
public interface CandidateProfileMigrationSource {

    /**
     * Loads the source profile to migrate from - throws {@link CandidateProfileMigrationSourceException}
     * if the source is missing or does not carry the minimum required fields, rather than
     * returning an empty/placeholder {@link CandidateProfile}.
     */
    CandidateProfile loadSourceProfile();
}
