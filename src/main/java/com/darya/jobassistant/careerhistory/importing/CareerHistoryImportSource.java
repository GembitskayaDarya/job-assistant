package com.darya.jobassistant.careerhistory.importing;

import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;

/**
 * Sprint 9 Step 7: the abstraction {@code CareerHistoryImportRunner}/{@code
 * CareerHistoryImportUseCase} depend on to obtain the external {@link CareerHistoryImportDocument}
 * to import - deliberately separate from any runtime Career History read path, mirroring {@code
 * CandidateProfileMigrationSource}'s convention. The one production implementation, {@code
 * YamlCareerHistoryImportSource}, exists as a Spring bean only while {@code
 * career-history.import.mode} is {@code DRY_RUN} or {@code APPLY}.
 */
public interface CareerHistoryImportSource {

    /**
     * Loads and strictly parses the configured source - throws {@code
     * CareerHistoryImportSourceException} if the source is missing, unreadable, too large,
     * malformed, or carries unknown properties, rather than returning a partial document.
     */
    CareerHistoryImportDocument load();
}
