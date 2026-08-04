package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link CareerHistoryRepositoryPort#save} when creating a new Career History that
 * references a {@code candidateProfileId} with no matching Candidate Profile row - {@code
 * career_history.candidate_profile_id}'s foreign key (V19) is the actual enforcement; this
 * translates that database relationship into a framework-free signal rather than letting a raw
 * {@code DataIntegrityViolationException} escape the port.
 */
public class CareerHistoryCandidateProfileNotFoundException extends RuntimeException {

    public CareerHistoryCandidateProfileNotFoundException(UUID candidateProfileId) {
        super("No candidate profile exists for candidateProfileId '" + candidateProfileId
                + "' - cannot create a career history for it");
    }
}
