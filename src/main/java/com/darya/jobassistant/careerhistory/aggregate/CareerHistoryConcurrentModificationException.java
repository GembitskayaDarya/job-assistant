package com.darya.jobassistant.careerhistory.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link CareerHistoryRepositoryPort#save} when the supplied {@link
 * CareerHistoryAggregate#version()} no longer matches the row's current version - another
 * transaction committed a change in between. Framework-free at this boundary, matching {@code
 * CandidateProfileConcurrentModificationException}'s convention: the persistence adapter never
 * lets a JPA/Hibernate exception type escape through the port. Carries only safe identifiers -
 * career history id, candidate profile id, and the expected version - never any company/position/
 * project/bullet/technology content.
 */
public class CareerHistoryConcurrentModificationException extends RuntimeException {

    public CareerHistoryConcurrentModificationException(UUID careerHistoryId, UUID candidateProfileId, long expectedVersion) {
        super("Career history '" + careerHistoryId + "' (candidateProfileId=" + candidateProfileId
                + ") was concurrently modified by another transaction - expected version " + expectedVersion
                + " no longer matches");
    }
}
