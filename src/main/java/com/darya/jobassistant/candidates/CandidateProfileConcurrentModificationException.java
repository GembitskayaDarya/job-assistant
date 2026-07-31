package com.darya.jobassistant.candidates;

/**
 * Thrown by {@link CandidateProfileRepositoryPort#save} when the supplied {@link
 * PersistedCandidateProfile#version()} no longer matches the row's current version - another
 * transaction committed a change in between. Translated from the persistence adapter's underlying
 * JPA/Hibernate optimistic-locking failure so this port never leaks a framework-specific exception
 * type; carries only the profile's safe identifiers (id/profile key), never its full contents.
 */
public class CandidateProfileConcurrentModificationException extends RuntimeException {

    public CandidateProfileConcurrentModificationException(String message) {
        super(message);
    }

    public CandidateProfileConcurrentModificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
