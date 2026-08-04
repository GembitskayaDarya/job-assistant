package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link CareerHistoryRepositoryPort#save} when creating a new Career History (a
 * supplied aggregate with a {@code null} {@link CareerHistoryAggregate#id()}) for a {@code
 * candidateProfileId} that already has one - {@code uk_career_history_candidate_profile_id} (V19)
 * enforces the underlying one-per-profile rule; this translates that database constraint into a
 * framework-free signal rather than letting a raw {@code DataIntegrityViolationException} escape
 * the port. Carries only the candidate profile id, never any Career History content.
 */
public class CareerHistoryAlreadyExistsException extends RuntimeException {

    public CareerHistoryAlreadyExistsException(UUID candidateProfileId) {
        super("A career history already exists for candidateProfileId '" + candidateProfileId + "'");
    }
}
