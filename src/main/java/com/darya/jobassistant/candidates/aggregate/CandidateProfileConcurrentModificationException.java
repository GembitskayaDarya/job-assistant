package com.darya.jobassistant.candidates.aggregate;

/**
 * Thrown by {@link CandidateProfileRepositoryPort#save} when the supplied {@link
 * CandidateProfileAggregate#version()} no longer matches the row's current version - another
 * transaction committed a change in between. Framework-free at this boundary: the persistence
 * adapter never lets a JPA/Hibernate exception type escape through the port. Carries only the
 * aggregate's safe identifiers (id/profile key), never its full contents.
 */
public class CandidateProfileConcurrentModificationException extends RuntimeException {

    public CandidateProfileConcurrentModificationException(String message) {
        super(message);
    }

    public CandidateProfileConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
