package com.darya.jobassistant.careerhistory.importing;

/**
 * Thrown by {@link CareerHistoryImportUseCase} when the source document's {@code
 * candidateProfileKey} does not resolve to any existing Candidate Profile via {@code
 * CandidateProfileRepositoryPort#findByProfileKey} - the import workflow never creates a
 * Candidate Profile automatically and never falls back to a hardcoded id. Framework-free, carries
 * only the business key - never any Career History content.
 */
public class CareerHistoryImportCandidateProfileNotFoundException extends RuntimeException {

    public CareerHistoryImportCandidateProfileNotFoundException(String candidateProfileKey) {
        super("No candidate profile exists for candidateProfileKey '" + candidateProfileKey
                + "' - career history import never creates a candidate profile automatically");
    }
}
