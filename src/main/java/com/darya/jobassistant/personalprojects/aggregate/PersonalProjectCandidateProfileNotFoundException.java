package com.darya.jobassistant.personalprojects.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link PersonalProjectRepositoryPort#save} when creating a new Personal Project that
 * references a {@code candidateProfileId} with no matching Candidate Profile row - {@code
 * personal_project.candidate_profile_id}'s foreign key (V29) is the actual enforcement; this
 * translates that database relationship into a framework-free signal.
 */
public class PersonalProjectCandidateProfileNotFoundException extends RuntimeException {

    public PersonalProjectCandidateProfileNotFoundException(UUID candidateProfileId) {
        super("No candidate profile exists for candidateProfileId '" + candidateProfileId
                + "' - cannot create a personal project for it");
    }
}
